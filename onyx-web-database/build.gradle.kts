/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("com.onyxdevtools.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

dependencies {
    implementation(project(":onyx-database"))
    implementation(project(":onyx-remote-database"))
    implementation(project(":onyx-remote-driver"))
    implementation("commons-cli:commons-cli:1.3.1")
    implementation("io.undertow:undertow-core:1.4.21.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.8.5")
}

description = "com.onyxdevtools:onyx-web-database"

java {
    withJavadocJar()
}
