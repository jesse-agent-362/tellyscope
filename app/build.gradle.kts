// SPDX-License-Identifier: GPL-3.0-only

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.tellyscope"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tellyscope"
        // 26 (Android 8.0): java.time and adaptive icons without desugaring, ~97% of
        // phones in use. Kadb needs 23; the TV side has its own floor (wireless debugging is 11+).
        minSdk = 26
        targetSdk = 37
        // A PR into main must raise both past the latest v* tag (.github/workflows/release-check.yml).
        versionCode = 1
        versionName = "0.0.1"

        // The rolling alpha (release.yml, on dev) passes -Ptellyscope.alpha=<run number>:
        // a separate app ID, so it installs next to the release.
        providers.gradleProperty("tellyscope.alpha").orNull?.let { run ->
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha.$run"
        }
    }

    // Only CI signs, from repo secrets; without RELEASE_KEYSTORE the release APK is unsigned.
    val keystore = providers.environmentVariable("RELEASE_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            // Bouncy Castle (via Kadb) ships the same license files in three jars.
            excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric renders the screenshot tests (see ScreenshotTest).
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":adb"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
