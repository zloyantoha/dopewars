plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dopewars.ru"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dopewars.ru"
        minSdk = 24
        targetSdk = 28
        versionCode = 4
        versionName = "0.4-kharkiv"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
