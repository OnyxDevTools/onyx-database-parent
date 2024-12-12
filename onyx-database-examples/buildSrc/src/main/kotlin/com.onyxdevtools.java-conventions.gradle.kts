/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    `java-library`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

group = "com.onyxdevtools"
version = "3.3.5"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
