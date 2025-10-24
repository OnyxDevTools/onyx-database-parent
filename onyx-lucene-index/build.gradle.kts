plugins {
    id("dev.onyx.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

description = "dev.onyx:onyx-lucene-index"

kotlin {
    jvmToolchain(Config.JAVA_VERSION)
}

dependencies {
    implementation(project(":onyx-database"))

    implementation("org.apache.lucene:lucene-core:${Config.LUCENE_VERSION}")
    implementation("org.apache.lucene:lucene-analyzers-common:${Config.LUCENE_VERSION}")
    implementation("org.apache.lucene:lucene-queryparser:${Config.LUCENE_VERSION}")
}

java {
    withJavadocJar()
}
