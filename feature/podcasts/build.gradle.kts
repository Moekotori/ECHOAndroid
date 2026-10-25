plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "app.echo.android.feature.podcasts"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.android)
}
