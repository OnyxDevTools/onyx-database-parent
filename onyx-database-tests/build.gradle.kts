/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("com.onyxdevtools.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

dependencies {
    implementation(project(":onyx-database"))
    implementation(project(":onyx-remote-database"))
    implementation(project(":onyx-web-database"))
    implementation(project(":onyx-remote-driver"))
    implementation("org.apache.httpcomponents:httpcore:4.4")
    implementation("org.apache.httpcomponents:httpclient:4.4")
    implementation("org.apache.httpcomponents:httpmime:4.4")
    implementation("org.springframework.security:spring-security-core:4.2.20.RELEASE")
    implementation("org.springframework:spring-core:4.3.20.RELEASE")
    implementation("org.springframework:spring-web:4.3.20.RELEASE")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client-jdk:1.10")
    implementation("commons-cli:commons-cli:1.3.1")
    implementation("io.undertow:undertow-core:1.4.21.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.8.5")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Config.KOTLIN_VERSION}")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.1.0")
}

description = "com.onyxdevtools:onyx-database-tests"

tasks.getByName<Test>("test") {
    useJUnit()
}

project.tasks.publish.configure {
    this.enabled = false
}