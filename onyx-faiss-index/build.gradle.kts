plugins {
    id("dev.onyx.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

description = "dev.onyx:onyx-faiss-index"

kotlin {
    jvmToolchain(Config.JAVA_VERSION)
}

dependencies {
    implementation(project(":onyx-database"))

    // FAISS JNI bindings - provides native FAISS vector search
    implementation("com.criteo.jfaiss:jfaiss-cpu:${Config.FAISS_VERSION}")
}

java {
    withJavadocJar()
}
