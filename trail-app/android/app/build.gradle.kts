plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ca.bancroft.trails"
    compileSdk = 37

    defaultConfig {
        applicationId = "ca.bancroft.trails"
        minSdk = 31
        targetSdk = 37
        versionCode = 6
        versionName = "6.0"
        ndk { abiFilters += listOf("arm64-v8a") } // Pixel phones are arm64; keeps the APK small
    }

    signingConfigs {
        // A fixed key kept in the repo so every build can update the installed app (sideload only, not for Play).
        create("sideload") {
            storeFile = rootProject.file("keystore/sideload.jks")
            storePassword = "bancroft-trails"
            keyAlias = "sideload"
            keyPassword = "bancroft-trails"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    androidResources {
        // Map tiles are already compressed; storing them uncompressed lets MapLibre read them directly.
        noCompress += listOf("pmtiles", "bin", "pbf")
    }
    packaging { jniLibs { useLegacyPackaging = false } }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                it.systemProperty("robolectric.dependency.repo.id", "central-mirror")
                (project.findProperty("screenshotBg") as String?)?.let { bg -> it.systemProperty("screenshot.bg", bg) }
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "4g"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.maplibre)
    testImplementation(libs.junit)
    // JVM screenshot tests of the UI panels (no device needed)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
