import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
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

/** Build calendar in Asia/Shanghai so CI (UTC) matches the product date. */
abstract class EchoCalendarDateSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
}

fun echoCalendarVersionName(date: LocalDate): String =
    "${date.year % 100}.${date.monthValue}.${date.dayOfMonth}"

fun echoCalendarVersionCode(date: LocalDate): Int {
    val y = date.year % 100
    return y * 10_000 + date.monthValue * 100 + date.dayOfMonth
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val echoBuildDate = LocalDate.parse(providers.of(EchoCalendarDateSource::class) {}.get())
val echoVersionName = echoCalendarVersionName(echoBuildDate)
val echoVersionCode = echoCalendarVersionCode(echoBuildDate)

the<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>().compilerOptions {
}

android {
    namespace = "app.echo.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.echo.android"
        minSdk = 26
        targetSdk = 36
        versionName = echoVersionName
        versionCode = echoVersionCode
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

    if (releaseSigningProperties.isNotEmpty()) {
        val releaseSigning = signingConfigs.create("release") {
            fun signingValue(name: String): String =
                requireNotNull(releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }) {
                    "Missing $name in local keystore.properties"
                }
            storeFile = rootProject.file(signingValue("storeFile"))
            storePassword = signingValue("storePassword")
            keyAlias = signingValue("keyAlias")
            keyPassword = signingValue("keyPassword")
        }
        buildTypes.getByName("release") {
            signingConfig = releaseSigning
        }
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

extensions.configure<BasePluginExtension>("base") {
    archivesName.set("ECHOAndroid-$echoVersionName")
}

dependencies {
    implementation(project(":core:i18n"))
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
    implementation("com.belerweb:pinyin4j:2.5.1")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
