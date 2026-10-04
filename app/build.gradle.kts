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
        versionCode = 1040
        versionName = "0.10.4"
        // The synth (app/src/main/cpp), brought over from Apogee.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O2", "-ffast-math")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }
    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
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
        prefab = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.oboe)
}

/**
 * Renders every sound to WAV on this machine with the phone's synth:
 * `./gradlew :app:soundGallery` writes them to the build folder's
 * sound-gallery and prints their peak and loudness. `-Praw` turns the limiter
 * off, for setting levels.
 */
tasks.register<Exec>("soundGallery") {
    group = "verification"
    description = "Renders every sound recipe to WAV files in build/sound-gallery"
    val out = layout.buildDirectory.dir("sound-gallery").get().asFile
    val cpp = file("src/main/cpp")
    val raw = project.hasProperty("raw")
    doFirst { out.mkdirs() }
    commandLine(
        "sh", "-c",
        "g++ -std=c++17 -O2 -ffast-math -o '${out}/gallery' '${cpp}/tools/sound_gallery.cpp' '${cpp}/synth/synth.cpp' " +
            "&& '${out}/gallery' '${out}'" + (if (raw) " raw" else ""),
    )
}
