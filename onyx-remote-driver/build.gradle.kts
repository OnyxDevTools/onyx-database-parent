/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("com.onyxdevtools.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

dependencies {
    implementation(project(":onyx-database"))
}

description = "com.onyxdevtools:onyx-remote-driver"

java {
    withJavadocJar()
}
