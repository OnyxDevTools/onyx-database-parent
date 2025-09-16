
description = "dev.onyx:onyx-cloud-client"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("dev.onyx.java-conventions")
}

dependencies {
    implementation("com.google.code.gson:gson:${Config.GSON_VERSION}")

}

java {
    withJavadocJar()
}
