/*
 * This file was generated by the Gradle 'init' task.
 */
val ktorVersion = "3.0.3"

plugins {
    id("com.onyxdevtools.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

dependencies {
    implementation(project(":onyx-database"))
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))

    // Core dependencies
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio") // Use CIO engine for HTTP and WebSocket support
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")

    // WebSockets feature
    implementation("io.ktor:ktor-client-websockets")

    // Serialization (if needed)
    implementation("io.ktor:ktor-client-serialization")

    // Logging (optional, for debugging)
    implementation("io.ktor:ktor-client-logging")
}

description = "com.onyxdevtools:onyx-remote-driver"

java {
    withJavadocJar()
}
