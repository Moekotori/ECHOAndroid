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
    // consumer-rules.pro keeps SMBJ's @Handler callbacks, but mbassador only reaches the build as a
    // transitive runtime dependency; compileOnly makes those class names resolvable for R8.
    compileOnly(libs.mbassador)

    testImplementation(libs.junit)
}
