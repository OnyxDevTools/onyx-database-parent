
description = "dev.onyx:onyx-cloud-client"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("dev.onyx.java-conventions")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:${Config.OK_HTTP_VERSION}")
    implementation("com.google.code.gson:gson:${Config.GSON_VERSION}")
    implementation("io.ktor:ktor-client-core:${Config.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp:${Config.KTOR_VERSION}")

    api("com.squareup.okhttp3:okhttp:${Config.OK_HTTP_VERSION}")
    api("io.ktor:ktor-client-okhttp-jvm:${Config.KTOR_VERSION}")
}

java {
    withJavadocJar()
}
