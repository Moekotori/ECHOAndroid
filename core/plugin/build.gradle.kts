plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.echo.android.plugin"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.rhino)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
