val ktorVersion = "3.0.3"
val gsonVersion = "2.8.9"
val okHttpVersion = "4.12.0"

description = "com.onyxdevtools:onyx-cloud-client"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("com.onyxdevtools.java-conventions")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")

    api("com.squareup.okhttp3:okhttp:$okHttpVersion")
    api("io.ktor:ktor-client-okhttp-jvm:3.0.3")
}

java {
    withJavadocJar()
}
