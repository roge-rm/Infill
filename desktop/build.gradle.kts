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
fun targetRuntime(name: String): Configuration = configurations.create(name) {
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
val debAmd64Runtime = targetRuntime("debAmd64Runtime")
val debArm64Runtime = targetRuntime("debArm64Runtime")
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.common)
    runtimeOnly(compose.desktop.currentOs)
    debAmd64Runtime(compose.desktop.linux_x64)
    debArm64Runtime(compose.desktop.linux_arm64)
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

// Use ccache if this machine has it. The build containers have their own, with a cache folder
// mounted in for each, because their versions differ from this machine's.
val ccache: Boolean = File("/usr/bin/ccache").canExecute()
val launcher = if (ccache) "-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache" else ""
/** Mounts and settings for a build container: the repo at the same path, and its compiler cache. */
fun containerArgs(image: String): String {
    val cache = File(System.getProperty("user.home"), ".cache/ccache-containers/$image")
    if (ccache) cache.mkdirs()
    return "-v '${rootProject.projectDir.absolutePath}':'${rootProject.projectDir.absolutePath}'" +
        if (ccache) " -v '$cache':/ccache -e CCACHE_DIR=/ccache" else ""
}

// The engine for 64-bit ARM, cross-compiled in a Debian Trixie container (native/Dockerfile.arm64).
// The repo root is mounted because the engine also builds the shared synth from app/src/main/cpp.
val nativeArm64 = layout.buildDirectory.dir("native-arm64")
val buildEngineArm64 = tasks.register<Exec>("buildEngineArm64") {
    inputs.dir("native")
    inputs.dir(rootProject.file("app/src/main/cpp/synth"))
    outputs.dir(nativeArm64)
    val out = nativeArm64.get().asFile.path
    val src = file("native").absolutePath
    workingDir = file("native")
    commandLine(
        "sh", "-c",
        "docker build -q -t infill-arm64-cross -f Dockerfile.arm64 . >/dev/null && " +
            "docker run --rm -u \$(id -u):\$(id -g) ${containerArgs("arm64-cross")} -w '$src' infill-arm64-cross sh -c '" +
            "J=/usr/lib/jvm/java-21-openjdk-amd64/include; " +
            "cmake -S . -B $out -DCMAKE_BUILD_TYPE=Release -DCMAKE_TOOLCHAIN_FILE=aarch64-linux-gnu.cmake $launcher \"-DJNI_INCLUDE_DIRS=\$J;\$J/linux\" >/dev/null && " +
            "cmake --build $out -j8'",
    )
}
compose.desktop {
    application {
        mainClass = "com.rm.infill.desktop.MainKt"
        jvmArgs += listOf("-Djava.library.path=${nativeBuild.get().asFile.absolutePath}", "--enable-native-access=ALL-UNNAMED")
    }
}
tasks.matching { it.name == "run" }.configureEach { dependsOn(buildEngine) }

fun registerDeb(arch: String, runtime: Configuration, engine: TaskProvider<Exec>, engineDir: Provider<Directory>) {
    val cap = arch.replaceFirstChar { it.uppercase() }
    val stage = layout.buildDirectory.dir("deb/$arch/root")
    val stageDeb = tasks.register<Sync>("stageDeb$cap") {
        dependsOn(engine)
        into(stage)
        filePermissions { unix("rw-r--r--") }
        dirPermissions { unix("rwxr-xr-x") }
        from(tasks.named("jar")) { into("usr/lib/infill/lib") }
        from(engineDir.map { it.file("libinfill_audio.so") }) { into("usr/lib/infill/native") }
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
    tasks.register("deb$cap") {
        group = "distribution"
        description = "Builds infill_${versionName}_$arch.deb for Debian Trixie"
        notCompatibleWithConfigurationCache("stages resolved artifacts and Debian metadata")
        dependsOn(stageDeb)
        val artifacts = runtime.incoming.artifacts.resolvedArtifacts
        val root = stage.get().asFile
        val output = layout.buildDirectory.file("deb/infill_${versionName}_$arch.deb").get().asFile
        val template = file("deb/control")
        val version = versionName
        inputs.files(runtime)
        inputs.dir(stage)
        inputs.file(template)
        inputs.property("version", versionName)
        outputs.file(output)
        doFirst {
            // The amd64 package uses the engine built on this machine; arm64 is cross-built in a container.
            check(System.getProperty("os.name") == "Linux" && (arch != "amd64" || System.getProperty("os.arch") in listOf("amd64", "x86_64"))) {
                "deb${cap} needs a Linux build host" + if (arch == "amd64") " on amd64 (Debian Trixie recommended)." else " with Docker."
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
            control.writeText(template.readText().replace("@VERSION@", version).replace("@ARCH@", arch).replace("@SIZE@", size.toString()))
            root.walkTopDown().forEach {
                Files.setPosixFilePermissions(it.toPath(), PosixFilePermissions.fromString(if (it.isDirectory || it == File(root, "usr/bin/infill")) "rwxr-xr-x" else "rw-r--r--"))
            }
            val process = ProcessBuilder("dpkg-deb", "--root-owner-group", "--build", root.path, output.path).inheritIO().start()
            check(process.waitFor() == 0) { "dpkg-deb failed" }
            control.parentFile.deleteRecursively()
        }
    }
}

registerDeb("amd64", debAmd64Runtime, buildEngine, nativeBuild)
registerDeb("arm64", debArm64Runtime, buildEngineArm64, nativeArm64)
