package com.ks.jaiplayer.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.LocaleList
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
    private lateinit var updater: GitHubUpdater
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
        updater = androidUpdater(this, settings, updatePlatformOf(form))
        app = JaiPlayerState(
            engine = engine,
            host = host(form),
            settings = settings,
            systemLanguages = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it].toLanguageTag() } },
            plugins = builtInPlugins(),
            updates = updater,
        )
        handleViewIntent(intent)
        setContent {
            // Immersive full screen and screen kept on while a video plays.
            LaunchedEffect(Unit) {
                snapshotFlow { app.screen == Screen.Player }.collect { inPlayer -> applyImmersive(inPlayer) }
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

    /** Phone: pressing Home while a video plays continues it in picture-in-picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val supported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (supported && app.host.formFactor == FormFactor.Phone && app.screen == Screen.Player && engine.state.value.isPlaying) {
            val (w, h) = engine.videoSize()
            val ratio = if (w > 0 && h > 0) Rational(w, h) else Rational(16, 9)
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(clamp(ratio)).build()) }
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
        updater.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) engine.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        override fun location(media: MediaRef) = Documents.location(media)
    }
}

/** Settings in SharedPreferences. */
private class PrefsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
