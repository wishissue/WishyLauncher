plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.wishy.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.wishy.launcher"
        minSdk = 21
        targetSdk = 34
        versionCode = 9
        versionName = "1.1.0"
    }

    // Release signing key, supplied by CI via environment variables (from GitHub
    // secrets). Absent locally and on F-Droid, so this config stays inert there.
    val ciKeystore = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ciKeystore != null) {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing priority:
            //  • CI (KEYSTORE_FILE env set)  → your real release key, from secrets.
            //  • -PlocalSign                 → the auto-generated debug key (local test).
            //  • otherwise (incl. F-Droid)   → UNSIGNED; F-Droid signs with its own key.
            signingConfig = when {
                ciKeystore != null -> signingConfigs.getByName("ci")
                project.hasProperty("localSign") -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    // Reproducibility for F-Droid: don't embed the Google-signed dependency
    // metadata block in the artifact.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // Bundled themes live in the repository's top-level themes/ folder and are packed into the APK as assets.
    sourceSets {
        getByName("main") {
            assets.srcDirs("../themes")
        }
    }
}

dependencies {
    implementation(project(":themecore"))
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    // iOS-style continuous (squircle) corners — perceptually smoother than the
    // circular-arc RoundedCornerShape.
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Applies the bundled baseline profile (src/main/baseline-prof.txt) so the
    // startup + scroll paths are AOT-compiled on first run instead of JIT'd —
    // the big cold-start "screen shows but not smooth yet" win.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Draws SVG logos into banners. Android has no SVG renderer of its own (VectorDrawable is a different format).
    // Apache-2.0, about 200 KB, no network or other dependencies. Only text that passed SvgSanitizer is given to it.
    implementation("com.caverock:androidsvg-aar:1.4")
}
