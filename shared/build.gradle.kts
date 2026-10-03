import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// What the Android app and the browser build have in common: the map renderer,
// the screens and the input. The Android app (:app) depends on this and the
// browser build is this module's wasmJs executable:
// ./gradlew :shared:wasmJsBrowserDistribution
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The synth as WebAssembly (web/synth/build.sh), served next to the page with
// its worklet. It needs Emscripten (emsdk in ~/.local/share/emsdk, or $EMSDK).
// Without it, the page is built silent.
val synthOut = layout.buildDirectory.dir("web-synth")
val emsdk = File(System.getenv("EMSDK") ?: "${System.getProperty("user.home")}/.local/share/emsdk")
val buildSynth = tasks.register<Exec>("buildSynth") {
    inputs.dir(rootProject.file("app/src/main/cpp/synth"))
    inputs.dir(rootProject.file("web/synth"))
    outputs.dir(synthOut)
    val hasEmsdk = File(emsdk, "emsdk_env.sh").exists()
    onlyIf("Emscripten is installed") { hasEmsdk }
    commandLine(rootProject.file("web/synth/build.sh").absolutePath, synthOut.get().asFile.absolutePath)
}

kotlin {
    android {
        namespace = "com.rm.infill.shared"
        compileSdk = 37
        minSdk = 27
        androidResources { enable = true }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "infill"
        browser {
            commonWebpackConfig {
                outputFileName = "infill.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":sim"))
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.material3)
            implementation(libs.jb.compose.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        wasmJsMain {
            resources.srcDir(files(synthOut).builtBy(buildSynth))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

compose.resources {
    packageOfResClass = "com.rm.infill.res"
    publicResClass = false
}

plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.set(null as String?)
}
