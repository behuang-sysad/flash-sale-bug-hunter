plugins {
    id("com.android.application")
}

android {
    namespace = "id.tsabit.signalmonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.tsabit.signalmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}