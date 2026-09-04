plugins {
    id("dev.onyx.java-conventions")
    kotlin("jvm") version Config.KOTLIN_VERSION
}

description = "dev.onyx:onyx-database-embeddings-onnx"

dependencies {
    api(project(":onyx-database"))
    implementation("com.microsoft.onnxruntime:onnxruntime:${Config.ONNX_RUNTIME_VERSION}")
    implementation("ai.djl.huggingface:tokenizers:${Config.HUGGING_FACE_TOKENIZERS_VERSION}")
    implementation("com.google.code.gson:gson:${Config.GSON_VERSION}")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Config.KOTLIN_VERSION}")
}

tasks.test {
    useJUnit()
}

java {
    withJavadocJar()
}

kotlin {
    jvmToolchain(Config.JAVA_VERSION)
}
