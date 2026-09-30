plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// infill.buildRoot in ~/.gradle/gradle.properties moves every module's build
// folder there, one folder per checkout, so a build can live in RAM. Without it
// everything builds in the usual place.
providers.gradleProperty("infill.buildRoot").orNull?.let { root ->
    val base = File(root, rootDir.name)
    allprojects {
        layout.buildDirectory.set(File(base, if (this == rootProject) "root" else path.removePrefix(":").replace(':', '/')))
    }
}

// The browser toolchain's repositories are declared in settings.gradle.kts, so
// stop the Kotlin plugin adding its own. Binaryen's is per project, see
// shared/build.gradle.kts.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
}
