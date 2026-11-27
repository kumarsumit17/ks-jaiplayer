package com.ks.jaiplayer.desktop

import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.update.GitHubUpdater
import com.ks.jaiplayer.update.OpenedOn
import com.ks.jaiplayer.update.UpdateInstaller
import com.ks.utility.appupdate.UpdatePlatform
import java.io.File
import kotlin.system.exitProcess

/** The running version: the packaged app and `run` pass it as -Djaiplayer.version (from the git tag). */
fun desktopAppVersion(): String = System.getProperty("jaiplayer.version")?.takeIf { it.isNotBlank() } ?: "0.0.0"

/**
 * Windows: starts the installer, then quits so the app can be replaced. macOS: opens the dmg,
 * shows the sentence for a few seconds, then quits too, so dragging the new app into
 * Applications replaces this one (Finder will not replace an app that is running).
 */
class DesktopInstaller(private val beforeQuit: () -> Unit) : UpdateInstaller {
    private val windows = System.getProperty("os.name").startsWith("Windows")

    override fun downloadDir(): File {
        val base = if (windows) {
            File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "KS JaiPlayer")
        } else {
            File(System.getProperty("user.home"), "Library/Caches/com.ks.jaiplayer")
        }
        return File(base, "update")
    }

    override fun open(file: File): OpenedOn {
        if (windows) {
            markFromInternet(file)
            val command = if (file.name.endsWith(".msi", true)) listOf("msiexec", "/i", file.path) else listOf(file.path)
            ProcessBuilder(command).start()
            beforeQuit()
            exitProcess(0)
        }
        // Quarantine, as for a browser download, so Gatekeeper checks it.
        runCatching { ProcessBuilder("xattr", "-w", "com.apple.quarantine", "0081;00000000;KS JaiPlayer;", file.path).start().waitFor() }
        ProcessBuilder("open", file.path).start().waitFor()
        Thread({
            Thread.sleep(MAC_QUIT_AFTER_MS)
            beforeQuit()
            exitProcess(0)
        }, "update-quit").apply { isDaemon = true }.start()
        return OpenedOn.Mac
    }

    /** Windows: the internet zone mark (Zone.Identifier), so SmartScreen checks the installer. */
    private fun markFromInternet(file: File) {
        runCatching { File(file.path + ":Zone.Identifier").writeText("[ZoneTransfer]\r\nZoneId=3\r\n") }
    }
}

fun desktopUpdater(settings: SettingsStore, beforeQuit: () -> Unit): GitHubUpdater? {
    val platform = UpdatePlatform.current() ?: return null
    return GitHubUpdater(GitHubUpdater.REPO, desktopAppVersion(), platform, settings, DesktopInstaller(beforeQuit)).also { it.start() }
}

/** How long the Mac app shows "the app now closes" before it quits for the update. */
private const val MAC_QUIT_AFTER_MS = 6_000L
