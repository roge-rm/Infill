import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from ../Keys/infill-keystore.properties, beside the
// project rather than in it, so neither the keystore nor its passwords can be
// committed. Without that file the release build is unsigned.
val signingProperties: Properties? = rootProject.file("../Keys/infill-keystore.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
val releaseStoreFile = signingProperties?.getProperty("storeFile")

android {
    namespace = "com.rm.infill"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.rm.infill"
        minSdk = 27
        targetSdk = 37
        versionCode = 100
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingProperties?.getProperty("storePassword")
                keyAlias = signingProperties?.getProperty("keyAlias")
                keyPassword = signingProperties?.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
