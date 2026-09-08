plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.echo.android.playback"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DECHO_FFMPEG_ROOT=${layout.buildDirectory.dir("ffmpeg/native").get().asFile}",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            buildStagingDirectory = layout.buildDirectory.dir("native-staging").get().asFile
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents.onVariants { variant ->
    variant.sources.java?.addStaticSourceDirectory("third_party/media3-ffmpeg/java")
    variant.sources.assets?.addStaticSourceDirectory("third_party/licenses")
}

// The vendored JNI/Java bridge must be reviewed together with Media3 upgrades.
check(libs.versions.media3.get() == file("third_party/media3-ffmpeg/VERSION").readText().trim()) {
    "Update the vendored Media3 FFmpeg bridge when upgrading Media3."
}

val buildFfmpeg by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the pinned audio-only FFmpeg libraries for Android"
    val script = rootProject.file("scripts/build-ffmpeg.py")
    val ndkDirectory = androidComponents.sdkComponents.ndkDirectory
    inputs.file(script)
    inputs.property("ndkVersion", android.ndkVersion!!)
    inputs.property("host", System.getProperty("os.name") + System.getProperty("os.arch"))
    outputs.dir(layout.buildDirectory.dir("ffmpeg/native"))
    commandLine(
        "python3", script.absolutePath,
        "--ndk", ndkDirectory.get().asFile.absolutePath,
        "--work", layout.buildDirectory.dir("ffmpeg").get().asFile.absolutePath,
    )
}

tasks.configureEach {
    if (name.startsWith("configureCMake")) dependsOn(buildFfmpeg)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:usb-audio"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    compileOnly("org.checkerframework:checker-qual:3.49.5")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}
