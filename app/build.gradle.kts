plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ua.pastushak.tvlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.pastushak.tvlauncher"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Один постійний ключ підпису: нові збірки встановлюються поверх старих без видалення.
    signingConfigs {
        create("shared") {
            storeFile = file("launcher.keystore")
            storePassword = "tvlauncher"
            keyAlias = "tvlauncher"
            keyPassword = "tvlauncher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
