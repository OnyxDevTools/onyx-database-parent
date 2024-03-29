/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("com.onyxdevtools.java-conventions")
}

dependencies {
    implementation(project(":onyx-database"))
    implementation(project(":onyx-remote-driver"))
    implementation("commons-cli:commons-cli:1.3.1")
}

description = "com.onyxdevtools:onyx-remote-database"

java {
    withJavadocJar()
}
