plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.echo.android.i18n"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
