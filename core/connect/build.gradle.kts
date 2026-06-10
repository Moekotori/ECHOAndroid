plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.echo.android.connect"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
