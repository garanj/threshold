plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.garan.threshold.fixtures.dummy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.garan.threshold.fixtures.dummy"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
