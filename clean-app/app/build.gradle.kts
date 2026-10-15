plugins {
    id("com.android.application")
}

android {
    namespace = "com.planify.app"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.planify.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "1.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
