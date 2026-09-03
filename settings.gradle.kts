// 国内网络环境常用镜像,默认关闭以免影响 CI(官方仓库在 GitHub Runner 上可达)。
// 本地启用: 设环境变量 ECHO_CN_MIRRORS=true
pluginManagement {
    repositories {
        if (System.getenv("ECHO_CN_MIRRORS") == "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("ECHO_CN_MIRRORS") == "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
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
