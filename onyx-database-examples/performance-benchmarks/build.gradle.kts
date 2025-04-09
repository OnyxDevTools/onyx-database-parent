/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    `maven-publish`
    application
}

application {
    mainClass.set("BenchmarkRunner")
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("org.hibernate:hibernate-core:5.2.2.Final")
    implementation("org.hibernate:hibernate-entitymanager:5.2.2.Final")
    implementation("com.h2database:h2:1.4.192")
    implementation("org.hsqldb:hsqldb:3.4.4")
    implementation("org.apache.derby:derby:10.12.1.1")
    implementation("org.xerial:sqlite-jdbc:3.8.7")
    implementation("com.onyxdevtools:onyx-database:3.5.16")
}

group = "com.onyxdevtools"
version = "1.0"
description = "com.onyxdevtools:onyx-database-examples:performance-benchmarks"
java.sourceCompatibility = JavaVersion.VERSION_17

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
