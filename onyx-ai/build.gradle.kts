import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "dev.onyx:onyx-ai"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("dev.onyx.java-conventions")
}

dependencies {
    implementation(project(":onyx-database"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

// Native library compilation for Metal GPU backend
val nativeOutputDir = layout.buildDirectory.dir("native/lib")
val nativeSourceDir = file("src/main/native/metal")

// Task to build Metal native library on macOS
val buildMetalLibrary = tasks.register<Exec>("buildMetalLibrary") {
    group = "native"
    description = "Builds the Metal native library for GPU acceleration"
    
    // Only run on macOS
    onlyIf {
        System.getProperty("os.name").lowercase().contains("mac")
    }
    
    workingDir = nativeSourceDir
    
    // Create output directory
    doFirst {
        nativeOutputDir.get().asFile.mkdirs()
    }
    
    // Build the native library
    commandLine("bash", "build.sh", nativeOutputDir.get().asFile.absolutePath)
    
    inputs.files(fileTree(nativeSourceDir))
    outputs.dir(nativeOutputDir)
}

// Task to copy native libraries to resources
val copyNativeLibraries = tasks.register<Copy>("copyNativeLibraries") {
    group = "native"
    description = "Copies native libraries to resources directory"
    dependsOn(buildMetalLibrary)
    
    from(nativeOutputDir)
    into("src/main/resources/native")
    
    onlyIf {
        nativeOutputDir.get().asFile.exists() && nativeOutputDir.get().asFile.listFiles()?.isNotEmpty() == true
    }
}

// Make sure native libraries are built before compilation
tasks.named("compileKotlin") {
    dependsOn(copyNativeLibraries)
}

tasks.named("processResources") {
    dependsOn(copyNativeLibraries)
}

// Fix dependency issue for sourcesJar
tasks.named("sourcesJar") {
    dependsOn(copyNativeLibraries)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector")
    
    // Add native library path for tests
    systemProperty("java.library.path", "${nativeOutputDir.get().asFile.absolutePath}:${file("src/main/resources/native").absolutePath}")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector")
    
    // Add native library path for execution
    systemProperty("java.library.path", "${nativeOutputDir.get().asFile.absolutePath}:${file("src/main/resources/native").absolutePath}")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// Clean native build artifacts
tasks.named("clean") {
    doLast {
        delete(nativeOutputDir)
        delete("src/main/resources/native")
    }
}