/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    application
}

application {
    mainClass.set("com.onyxdevtools.example.querying.Main")
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("com.onyxdevtools:onyx-database:3.5.13")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.30")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.4.30")
}

group = "com.onyxdevtools"
version = "3.5.13"
description = "com.onyxdevtools:onyx-database-examples:querying"
java.sourceCompatibility = JavaVersion.VERSION_17

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
