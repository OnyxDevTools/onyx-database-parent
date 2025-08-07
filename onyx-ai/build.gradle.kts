import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "com.onyxdevtools:onyx-ai"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("com.onyxdevtools.java-conventions")
}

dependencies {
    implementation(project(":onyx-database"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector")
}
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector")
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}