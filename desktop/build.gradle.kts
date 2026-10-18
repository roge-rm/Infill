import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(21) }

val versionName = Regex("versionName = \"([^\"]+)\"")
    .find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]

// Keep target runtimes separate from the host runtime as more packages are added.
val debAmd64Runtime = configurations.create("debAmd64Runtime") {
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get())
    val source = configurations.runtimeClasspath.get().attributes
    attributes {
        for (key in source.keySet()) {
            @Suppress("UNCHECKED_CAST")
            attribute(key as Attribute<Any>, source.getAttribute(key)!!)
        }
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.common)
    runtimeOnly(compose.desktop.currentOs)
    debAmd64Runtime(compose.desktop.linux_x64)
}

val nativeBuild = layout.buildDirectory.dir("native")
val configureEngine = tasks.register<Exec>("configureEngine") {
    inputs.dir("native")
    outputs.file(nativeBuild.map { it.file("CMakeCache.txt") })
    environment("JAVA_HOME", javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
        .get().metadata.installationPath.asFile.absolutePath)
    commandLine("cmake", "-S", file("native"), "-B", nativeBuild.get().asFile, "-DCMAKE_BUILD_TYPE=Release")
}
val buildEngine = tasks.register<Exec>("buildEngine") {
    dependsOn(configureEngine)
    inputs.dir("native")
    inputs.dir(rootProject.file("app/src/main/cpp/synth"))
    outputs.file(nativeBuild.map { it.file("libinfill_audio.so") })
    commandLine("cmake", "--build", nativeBuild.get().asFile, "--parallel")
}
compose.desktop {
    application {
        mainClass = "com.rm.infill.desktop.MainKt"
        jvmArgs += listOf("-Djava.library.path=${nativeBuild.get().asFile.absolutePath}", "--enable-native-access=ALL-UNNAMED")
    }
}
tasks.matching { it.name == "run" }.configureEach { dependsOn(buildEngine) }

val stage = layout.buildDirectory.dir("deb/amd64/root")
val stageDeb = tasks.register<Sync>("stageDebAmd64") {
    dependsOn(buildEngine)
    into(stage)
    filePermissions { unix("rw-r--r--") }
    dirPermissions { unix("rwxr-xr-x") }
    from(tasks.named("jar")) { into("usr/lib/infill/lib") }
    from(nativeBuild.map { it.file("libinfill_audio.so") }) { into("usr/lib/infill/native") }
    from("deb/infill") {
        into("usr/bin")
        filePermissions { unix("rwxr-xr-x") }
    }
    from("deb/infill.desktop") { into("usr/share/applications") }
    from(rootProject.file("shared/src/commonMain/composeResources/drawable/app_icon.png")) {
        into("usr/share/icons/hicolor/256x256/apps")
        rename { "infill.png" }
    }
    from("native/THIRD-PARTY-LICENSES.txt") { into("usr/share/doc/infill") }
    from(rootProject.file("LICENSE")) { into("usr/share/doc/infill"); rename { "copyright" } }
}

tasks.register("debAmd64") {
    group = "distribution"
    description = "Builds infill_${versionName}_amd64.deb for Debian Trixie"
    notCompatibleWithConfigurationCache("stages resolved artifacts and Debian metadata")
    dependsOn(stageDeb)
    val artifacts = debAmd64Runtime.incoming.artifacts.resolvedArtifacts
    val root = stage.get().asFile
    val output = layout.buildDirectory.file("deb/infill_${versionName}_amd64.deb").get().asFile
    val template = file("deb/control")
    val version = versionName
    inputs.files(debAmd64Runtime)
    inputs.dir(stage)
    inputs.file(template)
    inputs.property("version", versionName)
    outputs.file(output)
    doFirst {
        check(System.getProperty("os.name") == "Linux" && System.getProperty("os.arch") in listOf("amd64", "x86_64")) {
            "debAmd64 requires a Linux amd64 build host (Debian Trixie recommended)."
        }
    }
    doLast {
        val lib = File(root, "usr/lib/infill/lib")
        for (artifact in artifacts.get()) {
            val id = artifact.id.componentIdentifier
            val name = if (id is org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                "${id.group}-${artifact.file.name}" else artifact.file.name
            artifact.file.copyTo(File(lib, name), overwrite = true)
        }
        val size = root.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
        val control = File(root, "DEBIAN/control")
        control.parentFile.mkdirs()
        control.writeText(template.readText().replace("@VERSION@", version).replace("@SIZE@", size.toString()))
        root.walkTopDown().forEach {
            Files.setPosixFilePermissions(it.toPath(), PosixFilePermissions.fromString(if (it.isDirectory || it == File(root, "usr/bin/infill")) "rwxr-xr-x" else "rw-r--r--"))
        }
        val process = ProcessBuilder("dpkg-deb", "--root-owner-group", "--build", root.path, output.path).inheritIO().start()
        check(process.waitFor() == 0) { "dpkg-deb failed" }
        control.parentFile.deleteRecursively()
    }
}
