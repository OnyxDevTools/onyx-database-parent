/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    application
}

application {
    mainClass.set("com.onyxdevtools.client.Main")
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("com.onyxdevtools:onyx-database:3.5.16")
    implementation("com.onyxdevtools:onyx-remote-driver:3.5.16")
    implementation(project(":data-model"))
}

group = "com.onyxdevtools"
version = "3.5.16"
description = "com.onyxdevtools:onyx-database-examples:remote-persistence-manager:onyx-database-client"
java.sourceCompatibility = JavaVersion.V

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
