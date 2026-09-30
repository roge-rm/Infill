import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The city and everything that happens in it. Plain Kotlin with no UI or
// platform code, so the whole simulation can be tested on the JVM:
// ./gradlew :sim:jvmTest
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvm()

    android {
        namespace = "com.rm.infill.sim"
        compileSdk = 37
        minSdk = 27
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.set(null as String?)
}
