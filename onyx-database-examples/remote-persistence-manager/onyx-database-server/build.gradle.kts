/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    application
}

application {
    mainClass.set("com.onyxdevtools.server.Main")
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("com.onyxdevtools:onyx-database:3.4.4")
    implementation("com.onyxdevtools:onyx-remote-driver:3.4.4")
    implementation("com.onyxdevtools:onyx-remote-database:3.4.4")
    implementation(project(":data-model"))
}

group = "com.onyxdevtools"
version = "3.4.4"
description = "com.onyxdevtools:onyx-database-examples:remote-persistence-manager:onyx-database-server"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
