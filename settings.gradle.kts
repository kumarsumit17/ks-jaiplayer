pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ks-jaiplayer"

// Generic KS-Suite helpers, from the pinned submodule (com.ks:utility).
includeBuild("ks-utility/kotlin")

include(":shared")
include(":desktop:mac", ":desktop:windows")
// A sample third-party plugin for the desktop apps; never part of an installer.
include(":desktop:plugin-sample")
include(":mobile:android", ":tv:google-tv")
