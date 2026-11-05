plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bona.fileflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bona.fileflow"
        minSdk = 24
        targetSdk = 35
        // Give every CI build a higher version so Android can install it as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "1.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "local"}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity:1.10.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
