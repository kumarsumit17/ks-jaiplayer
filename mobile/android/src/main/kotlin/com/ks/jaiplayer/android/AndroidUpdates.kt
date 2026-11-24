package com.ks.jaiplayer.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ks.jaiplayer.settings.SettingsStore
import com.ks.jaiplayer.tv.isTelevision
import com.ks.jaiplayer.update.GitHubUpdater
import com.ks.jaiplayer.update.OpenedOn
import com.ks.jaiplayer.update.UpdateInstaller
import com.ks.utility.appupdate.UpdatePlatform
import java.io.File

/** Hands the verified APK to the system installer through a FileProvider URI. */
class AndroidInstaller(private val activity: Activity) : UpdateInstaller {
    override fun downloadDir() = File(activity.cacheDir, "update")

    override fun canInstall(): Boolean = activity.packageManager.canRequestPackageInstalls()

    override fun open(file: File): OpenedOn {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        return OpenedOn.Android
    }

    override fun openPermissionSettings() {
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
    }
}

fun androidUpdater(activity: Activity, settings: SettingsStore): GitHubUpdater {
    val version = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "0.0.0"
    // A TV asks for the TV build (…-google-tv.apk) and a phone for the phone build, so neither
    // ever installs the other's; while a release has one APK for both, a TV takes that one.
    val platform = if (isTelevision(activity)) UpdatePlatform.GOOGLE_TV else UpdatePlatform.ANDROID
    return GitHubUpdater(GitHubUpdater.REPO, version, platform, settings, AndroidInstaller(activity)).also { it.start() }
}
