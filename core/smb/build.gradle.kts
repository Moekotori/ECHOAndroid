plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.echo.android.smb"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
