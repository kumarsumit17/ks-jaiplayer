package com.ks.jaiplayer.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.plugins.builtInPlugins
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.ui.FormFactor
import com.ks.jaiplayer.ui.JaiPlayerApp
import com.ks.jaiplayer.ui.JaiPlayerState
import com.ks.jaiplayer.ui.MediaLibrary
import com.ks.jaiplayer.ui.PlatformHost
import com.ks.jaiplayer.ui.Screen
import com.ks.jaiplayer.update.GitHubUpdater

/**
 * The one activity of both Android apps, declared in this module's manifest. The phone app
 * (mobile/android) and the Google TV app (tv/google-tv) only add their launcher entry; which one
 * this is comes from the manifest ([formFactorOf]), never from the device.
 */
class MainActivity : ComponentActivity() {
    private lateinit var app: JaiPlayerState
    private lateinit var engine: ExoPlayerEngine
    private lateinit var session: PlayerSession
    private lateinit var pipActions: PipActions
    /** Whether picture-in-picture may start by itself now (Android 12+); kept for refreshes. */
    private var pipAutoEnter = false
    /** Null in the Google Play build, which Play updates. */
    private var updater: GitHubUpdater? = null
    private var onMediaPicked: ((MediaRef) -> Unit)? = null
    private var onFolderPicked: ((List<MediaRef>) -> Unit)? = null
    private var onSubtitlePicked: ((String) -> Unit)? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        Documents.keepAccess(this, uri)
        onMediaPicked?.invoke(Documents.mediaRef(this, uri))
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@registerForActivityResult
        Documents.keepAccess(this, tree)
        val items = Documents.mediaInTree(this, tree)
        if (items.isNotEmpty()) onFolderPicked?.invoke(items)
    }
    private var onPermission: ((Boolean) -> Unit)? = null
    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onPermission?.invoke(result.values.any { it })
    }
    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        onSubtitlePicked?.invoke(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val form = formFactorOf(this)
        engine = ExoPlayerEngine(this)
        val settings = PrefsStore(getSharedPreferences("settings", MODE_PRIVATE))
        updater = if (isPlayBuild(this)) null else androidUpdater(this, settings, updatePlatformOf(form))
        app = JaiPlayerState(
            engine = engine,
            host = host(form),
            settings = settings,
            systemLanguages = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it].toLanguageTag() } },
            plugins = builtInPlugins(),
            updates = updater,
        )
        session = PlayerSession(
            this, engine,
            hasNext = { app.playlist?.hasNext == true },
            hasPrevious = { app.playlist?.hasPrevious == true },
            onNext = app::next,
            onPrevious = app::previous,
        )
        pipActions = PipActions(this) { app.controller.perform(it) }.also { it.register() }
        // Back closes the browser or the video before it leaves the app. On TV the player's
        // controls take the remote's keys while they show, so Compose never sees Back then.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    app.browse != null -> app.browse = null
                    app.screen == Screen.Player -> app.closePlayer()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
        handleViewIntent(intent)
        setContent {
            // Immersive full screen and screen kept on while a video plays.
            LaunchedEffect(Unit) {
                snapshotFlow { app.screen == Screen.Player }.collect { inPlayer -> applyImmersive(inPlayer) }
            }
            LaunchedEffect(Unit) { snapshotFlow { app.playlist }.collect { session.playlistChanged() } }
            // Phone, Android 12+: swiping home while a video plays moves it to picture-in-picture
            // smoothly (auto-enter); the params follow the video's shape.
            if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LaunchedEffect(Unit) {
                combine(snapshotFlow { app.screen == Screen.Player }, engine.state.map { it.isPlaying }) { inPlayer, playing -> inPlayer && playing }
                    .combine(engine.state.map { engine.videoSize() }) { auto, _ -> auto }
                    .distinctUntilChanged()
                    .collect { auto -> pipAutoEnter = auto; setPictureInPictureParams(pipParams(auto)) }
            }
            // Android 8 to 12: the play/pause button in the window follows the playback state.
            if (pipSupported && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) LaunchedEffect(Unit) {
                engine.state.map { it.isPlaying }.distinctUntilChanged()
                    .collect { runCatching { setPictureInPictureParams(pipParams(pipAutoEnter)) } }
            }
            JaiPlayerApp(app)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** ACTION_VIEW from another app (ks-streamflix, ks-tv-launcher, a file manager). */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        app.open(Documents.mediaRef(this, uri, intent.getStringExtra(Intent.EXTRA_TITLE)))
    }

    private fun applyImmersive(on: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, !on)
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Picture-in-picture is for the phone app only. */
    private val pipSupported: Boolean
        get() = app.host.formFactor == FormFactor.Phone && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val (w, h) = engine.videoSize()
        val ratio = if (w > 0 && h > 0) Rational(w, h) else Rational(16, 9)
        val builder = PictureInPictureParams.Builder().setAspectRatio(clamp(ratio))
            .setActions(pipActions.actions(engine.state.value.isPlaying))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Video is not resized seamlessly (that is for other content); the system cross-fades it.
            builder.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    /** Phone, Android 8–11: pressing Home while a video plays continues it in picture-in-picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return // auto-enter does it
        if (pipSupported && app.screen == Screen.Player && engine.state.value.isPlaying) {
            runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
        }
    }

    /** Android allows picture-in-picture ratios between 1:2.39 and 2.39:1. */
    private fun clamp(r: Rational): Rational {
        val v = r.toFloat()
        return when {
            v > 2.39f -> Rational(239, 100)
            v < 1 / 2.39f -> Rational(100, 239)
            else -> r
        }
    }

    override fun onResume() {
        super.onResume()
        updater?.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) engine.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // A new activity makes its own session, and two sessions may not share an id.
        session.release()
        pipActions.unregister()
        if (isFinishing) engine.release()
    }

    private fun host(form: FormFactor) = object : PlatformHost {
        override val formFactor = form

        // Google TV often has no system file picker, so the TV app browses the media library.
        override val library: MediaLibrary? = if (form == FormFactor.Tv) {
            AndroidMediaLibrary(this@MainActivity) { permissions, onResult ->
                onPermission = onResult
                askPermission.launch(permissions)
            }
        } else null

        override fun pickMedia(onPicked: (MediaRef) -> Unit) {
            onMediaPicked = onPicked
            pickMedia.launch(arrayOf("video/*", "audio/*"))
        }

        override fun pickFolder(onPicked: (List<MediaRef>) -> Unit) {
            onFolderPicked = onPicked
            pickFolder.launch(null)
        }

        override fun pickSubtitle(onPicked: (String) -> Unit) {
            onSubtitlePicked = onPicked
            pickSubtitle.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "application/octet-stream", "*/*"))
        }

        override fun exists(media: MediaRef) = Documents.exists(this@MainActivity, media)
        override fun location(media: MediaRef) = Documents.location(this@MainActivity, media)
    }
}

/** Settings in SharedPreferences. */
private class PrefsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
