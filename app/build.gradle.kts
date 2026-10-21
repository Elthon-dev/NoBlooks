plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.elthon.optilauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elthon.optilauncher"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.3"

        // Exposed at runtime so the diagnostics page can state the real build contract
        // instead of guessing from Build.VERSION.
        buildConfigField("int", "CFG_MIN_SDK", "29")
        buildConfigField("int", "CFG_COMPILE_SDK", "35")
        buildConfigField("int", "CFG_TARGET_SDK", "35")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Compose is dramatically slower in a debuggable build (the whole UI
            // stack runs interpreted), so the release variant is the artifact to
            // judge smoothness with. Signed with the debug key so CI produces an
            // installable APK; this is not a Play Store signing setup.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        // Shizuku's public API no longer exposes newProcess; the supported way to run
        // shell commands is a UserService bound over AIDL.
        aidl = true
    }

    lint {
        // The legacy recon probes still inspect APIs whose public surface moved between
        // releases; lint stays informational (lintDebug still runs and is reviewed).
        abortOnError = false
        warningsAsErrors = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = false
        }
    }
}

// Note: AGP 8.7's public variant API has no outputFileName property, so CI names the
// shipped files (optilauncher-debug.apk / optilauncher-release.apk) when it collects
// artifacts instead of poking at internal Gradle classes.

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Installs the baseline profiles shipped by Compose/AndroidX (and any profile
    // bundled in the APK), so first-run frames use AOT-compiled code paths.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // The always-on overlay is hosted by a LifecycleService in the app process; a
    // Service has no lifecycle/saved-state owners of its own, so Compose needs these.
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.savedstate:savedstate:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Shizuku: elevated access for the control centre's radio tiles. Without a
    // running Shizuku service every call degrades to the honest read-only tiles;
    // with it, Wi-Fi and mobile data become real, distinguishable switches.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
}
