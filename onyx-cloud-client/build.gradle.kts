
description = "dev.onyx:onyx-cloud-client"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("dev.onyx.java-conventions")
}

java {
    withJavadocJar()
}
