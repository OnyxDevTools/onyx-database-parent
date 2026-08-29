
description = "dev.onyx:onyx-cloud-client"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("dev.onyx.java-conventions")
}

dependencies {
    implementation("com.google.code.gson:gson:${Config.GSON_VERSION}")
    implementation("org.msgpack:msgpack-core:0.9.12")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

java {
    withJavadocJar()
}

kotlin {
    jvmToolchain(Config.JAVA_VERSION)
}
