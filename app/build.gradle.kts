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

    signingConfigs {
        val signingStoreFile = System.getenv("SIGNING_STORE_FILE")
        if (!signingStoreFile.isNullOrBlank()) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
