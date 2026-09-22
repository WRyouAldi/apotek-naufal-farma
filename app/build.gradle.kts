plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.naufalfarma.apotek"
    compileSdk = 35

    // GitHub Actions supplies a monotonically increasing build number.
    // Local builds fall back to the existing app version code.
    val ciBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER")
        .orNull?.toIntOrNull() ?: 14

    defaultConfig {
        applicationId = "com.naufalfarma.apotek"
        minSdk = 23
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "1.4.$ciBuildNumber"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
