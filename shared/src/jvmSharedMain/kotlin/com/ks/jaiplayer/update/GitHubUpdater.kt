package com.ks.jaiplayer.update

import com.ks.jaiplayer.settings.SettingsStore
import com.ks.utility.appupdate.AppUpdate
import com.ks.utility.appupdate.GitHubRepo
import com.ks.utility.appupdate.ReleaseAsset
import com.ks.utility.appupdate.Sha256
import com.ks.utility.appupdate.UpdateOutcome
import com.ks.utility.appupdate.UpdatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** The platform's part: where downloads go and how the verified file reaches the installer. */
interface UpdateInstaller {
    /** A folder of the app's own, emptied before each download. */
    fun downloadDir(): File

    /** True when the system lets this app install the file (Android's "install unknown apps"). */
    fun canInstall(): Boolean = true

    /** Hands the verified file to the system; returns where, for the sentence shown. */
    fun open(file: File): OpenedOn

    fun openPermissionSettings() {}
}

/**
 * Checks this app's GitHub releases and installs a newer version, following ks-utility's
 * `specs/app-update.md`: one unauthenticated GET of `releases/latest`, at most once a day by
 * itself (10 s after start, then hourly if due), never by itself for a build from source; the
 * download's SHA-256 must match the one GitHub lists, or nothing is opened.
 */
class GitHubUpdater(
    repo: String,
    override val currentVersion: String,
    private val platform: UpdatePlatform,
    private val settings: SettingsStore,
    private val installer: UpdateInstaller,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : UpdateService {
    private val repo = requireNotNull(GitHubRepo.parse(repo)) { "Not owner/name: $repo" }
    override val isDevBuild = AppUpdate.parseVersion(currentVersion) == null || AppUpdate.isDevVersion(currentVersion)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    override val status: StateFlow<UpdateStatus> = _status
    private val _auto = MutableStateFlow(settings.getString(AUTO_KEY) != "false")
    override val autoCheck: StateFlow<Boolean> = _auto

    private var found: Pair<String, ReleaseAsset>? = null
    private var job: Job? = null

    /** Starts the automatic schedule. */
    fun start() {
        scope.launch {
            delay(AppUpdate.STARTUP_DELAY_MS)
            while (isActive) {
                checkIfDue()
                delay(AppUpdate.RECHECK_EVERY_MS)
            }
        }
    }

    /** Android and TV: also when the app comes back to the foreground. */
    fun onForeground() {
        scope.launch { checkIfDue() }
    }

    private fun checkIfDue() {
        val last = settings.getString(LAST_CHECK_KEY)?.toLongOrNull()
        if (job?.isActive != true && AppUpdate.isCheckDue(_auto.value, currentVersion, last, System.currentTimeMillis())) check(silent = true)
    }

    override fun setAutoCheck(enabled: Boolean) {
        settings.putString(AUTO_KEY, enabled.toString())
        _auto.value = enabled
    }

    override fun checkNow() {
        if (job?.isActive == true) return
        job = scope.launch { check(silent = false) }
    }

    private fun check(silent: Boolean) {
        if (!silent) _status.value = UpdateStatus.Checking
        val body = try {
            fetchLatest()
        } catch (e: IOException) {
            if (!silent) _status.value = UpdateStatus.Failed(UpdateFailure.Check, e.message ?: e.javaClass.simpleName)
            return
        }
        settings.putString(LAST_CHECK_KEY, System.currentTimeMillis().toString())
        when (val outcome = AppUpdate.evaluateReleases(body, repo, currentVersion, platform)) {
            null -> if (!silent) _status.value = UpdateStatus.Failed(UpdateFailure.Unreadable)
            is UpdateOutcome.UpToDate -> if (!silent) _status.value = UpdateStatus.UpToDate(outcome.version)
            is UpdateOutcome.NoInstaller -> if (!silent) _status.value = UpdateStatus.NoInstaller(outcome.version)
            is UpdateOutcome.Available -> {
                found = outcome.version to outcome.asset
                _status.value = UpdateStatus.Available(outcome.version, outcome.asset.size)
            }
        }
    }

    private fun fetchLatest(): String {
        val connection = URI(repo.releasesApiUrl).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            repo.requestHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            if (code != 200) throw IOException("HTTP $code")
            return connection.inputStream.use { input ->
                val bytes = input.readNBytes(AppUpdate.MAX_RESPONSE_BYTES)
                bytes.decodeToString()
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun install() {
        val (version, asset) = found ?: return
        if (job?.isActive == true) return
        if (!installer.canInstall()) {
            _status.value = UpdateStatus.NeedsPermission
            return
        }
        job = scope.launch {
            _status.value = UpdateStatus.Downloading(version)
            val dir = installer.downloadDir().apply { deleteRecursively(); mkdirs() }
            val file = File(dir, asset.name)
            try {
                download(asset.url, file)
            } catch (e: IOException) {
                file.delete()
                _status.value = UpdateStatus.Failed(UpdateFailure.Download, e.message ?: e.javaClass.simpleName)
                return@launch
            }
            if (!Sha256.matches(Sha256.hex(file), asset.sha256)) {
                file.delete()
                _status.value = UpdateStatus.Failed(UpdateFailure.Damaged)
                return@launch
            }
            _status.value = try {
                UpdateStatus.Opened(installer.open(file))
            } catch (e: Exception) {
                UpdateStatus.Failed(UpdateFailure.Open, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun openInstallPermissionSettings() = installer.openPermissionSettings()

    private fun download(url: String, to: File) {
        var current = URI(url).toURL()
        // GitHub redirects release downloads to its file host; follow a few redirects over HTTPS only.
        repeat(5) {
            val c = current.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 60_000
                c.setRequestProperty("User-Agent", "ks-jaiplayer")
                when (val code = c.responseCode) {
                    200 -> {
                        c.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
                        return
                    }
                    301, 302, 303, 307, 308 -> {
                        val next = URI(current.toString()).resolve(c.getHeaderField("Location") ?: throw IOException("HTTP $code"))
                        if (next.scheme != "https") throw IOException("Refused redirect to ${next.scheme}")
                        current = next.toURL()
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    companion object {
        const val REPO = "kumarsumit17/ks-jaiplayer"
        private const val AUTO_KEY = "update.auto"
        private const val LAST_CHECK_KEY = "update.lastCheck"
    }
}
