plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.echo.android.feature.settings"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Package the canonical texts rather than maintaining a second copy in settings.
abstract class OpenSourceLicenseAssets : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val prepareOpenSourceLicenses by tasks.registering(OpenSourceLicenseAssets::class) {
    outputDirectory.set(layout.buildDirectory.dir("generated/openSourceAssets"))
    into(outputDirectory.dir("echo-licenses"))
    from(rootProject.file("LICENSE")) { rename { "ECHO-AGPL-3.0.txt" } }
    from(rootProject.file("core/playback/third_party/licenses")) { into("audio") }
    from(rootProject.file("core/data/src/main/assets/autoeq")) {
        include("LICENSE.txt", "NOTICE.txt")
        into("autoeq")
    }
    from(rootProject.file("feature/player/src/main/assets/fonts")) {
        include("*.txt")
        into("fonts")
    }
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(prepareOpenSourceLicenses, OpenSourceLicenseAssets::outputDirectory)
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
