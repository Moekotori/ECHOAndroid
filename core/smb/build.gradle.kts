plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.echo.android.smb"
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
    implementation(libs.smbj)

    testImplementation(libs.junit)
}
