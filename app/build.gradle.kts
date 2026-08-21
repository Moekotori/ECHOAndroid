import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun lastFmBuildValue(name: String, fallback: String = ""): String {
    val value = providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)
        ?: fallback
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

the<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>().compilerOptions {
}

android {
    namespace = "app.echo.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.echo.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-foundation"
        buildConfigField(
            "String",
            "LASTFM_API_KEY",
            "\"${lastFmBuildValue("LASTFM_API_KEY")}\"",
        )
        buildConfigField(
            "String",
            "LASTFM_SHARED_SECRET",
            "\"${lastFmBuildValue("LASTFM_SHARED_SECRET")}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:playback"))
    implementation(project(":core:connect"))
    implementation(project(":core:design"))
    implementation(project(":core:lyrics"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:connect"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.paging.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation("com.belerweb:pinyin4j:2.5.1")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
