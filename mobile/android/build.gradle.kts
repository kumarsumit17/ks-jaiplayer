// The phone app. Everything it runs is in :shared (androidMain); this module adds the phone
// launcher entry. Package name, version and signing: the root build.gradle.kts, shared with the
// Google TV app (tv/google-tv), which is a separate APK.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ks.jaiplayer.phone"
}
