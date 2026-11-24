package com.ks.jaiplayer.android

import android.content.Context
import android.content.pm.PackageManager
import com.ks.jaiplayer.ui.FormFactor
import com.ks.utility.appupdate.UpdatePlatform

/** The manifest meta-data that marks the Google TV app (tv/google-tv); the phone app has none. */
const val FORM_FACTOR_KEY = "com.ks.jaiplayer.FORM_FACTOR"

/**
 * Which app this is: the phone app (mobile/android) or the Google TV app (tv/google-tv). They
 * are separate APKs with the same package name, so it is a fact of the build, not of the device:
 * the TV app's manifest sets [FORM_FACTOR_KEY] to `tv`.
 */
fun formFactorOf(context: Context): FormFactor {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    return if (info.metaData?.getString(FORM_FACTOR_KEY) == "tv") FormFactor.Tv else FormFactor.Phone
}

/** Each app updates to its own build: the TV app to …-google-tv.apk, the phone app to the plain APK. */
fun updatePlatformOf(form: FormFactor): UpdatePlatform =
    if (form == FormFactor.Tv) UpdatePlatform.GOOGLE_TV else UpdatePlatform.ANDROID
