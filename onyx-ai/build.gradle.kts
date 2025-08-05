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
    jvmToolchain(17)
}
