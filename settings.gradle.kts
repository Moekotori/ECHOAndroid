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

rootProject.name = "ECHOAndroid"

include(":app")
include(":core:data")
include(":core:model")
include(":core:usb-audio")
include(":core:playback")
include(":core:connect")
include(":core:design")
include(":core:lyrics")
include(":feature:home")
include(":feature:library")
include(":feature:player")
include(":feature:connect")
include(":feature:settings")

include(":core:i18n")
