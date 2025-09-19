
description = "dev.onyx:onyx-cloud-client"

plugins {
    id("dev.onyx.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

dependencies {
    implementation("com.google.code.gson:gson:${Config.GSON_VERSION}")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

java {
    withJavadocJar()
}
