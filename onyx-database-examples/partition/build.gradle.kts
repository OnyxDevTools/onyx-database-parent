/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    application
}

application {
    mainClass.set("com.onyxdevtools.partition.Main")
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("com.onyxdevtools:onyx-database:3.4.2")
}

group = "com.onyxdevtools"
version = "3.4.2"
description = "com.onyxdevtools:onyx-database-examples:partition"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
