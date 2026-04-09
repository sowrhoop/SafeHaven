import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.serialization)
}

android {
    namespace = "app.anonymous.safehaven"
    compileSdk = 36

    signingConfigs {
        create("releaseSignature") {
            storeFile = file(project.findProperty("StoreFile")?.toString() ?: "safehaven-release-key.keystore")
            storePassword = (project.findProperty("SafeHavenStorePassword") as? String) ?: System.getenv("SAFEHAVEN_STORE_PASS")
            keyPassword = (project.findProperty("SafeHavenKeyPassword") as? String) ?: System.getenv("SAFEHAVEN_KEY_PASS")
            keyAlias = (project.findProperty("SafeHavenKeyAlias") as? String) ?: System.getenv("SAFEHAVEN_KEY_ALIAS") ?: "safehaven"
        }
    }

    defaultConfig {
        applicationId = "app.anonymous.safehaven"
        minSdk = 26
        targetSdk = 36
        
        // --- DYNAMIC VERSIONING ---
        // Reads from CI/CD CLI arguments, falls back to local dev defaults
        versionCode = (project.findProperty("AppVersionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("AppVersionName") as? String) ?: "1.0"

        // --- OPEN SOURCE PRIVACY INJECTION ---
        val userWhitelist = System.getenv("SAFEHAVEN_WHITELIST") ?: (project.findProperty("SafeHavenWhitelist") as? String) ?: "app.anonymous.safehaven,com.android.settings"
        val userBatteryFlags = System.getenv("SAFEHAVEN_BATTERY_FLAGS") ?: (project.findProperty("SafeHavenBatteryFlags") as? String) ?: "advertise_is_enabled=true"
        val userDns = System.getenv("SAFEHAVEN_DNS") ?: (project.findProperty("SafeHavenDns") as? String) ?: "dns.google"

        buildConfigField("String", "APP_WHITELIST", "\"$userWhitelist\"")
        buildConfigField("String", "BATTERY_SAVER_FLAGS", "\"$userBatteryFlags\"")
        buildConfigField("String", "PRIVATE_DNS", "\"$userDns\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("releaseSignature")
        }
        debug {
            signingConfig = signingConfigs.getByName("releaseSignature")
        }
        create("fastDebug") {
            initWith(getByName("debug"))
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true 
        aidl = false 
        resValues = false
        shaders = false
        renderScript = false
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += listOf("MissingTranslation", "GoogleAppIndexingWarning")
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
    sourceSets {
        all { languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi") }
    }
}

dependencies {
    implementation(libs.serialization)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(kotlin("reflect"))
}