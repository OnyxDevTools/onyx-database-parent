import org.gradle.api.JavaVersion

object Config {
    // Onyx Version
    const val ONYX_VERSION = "4.2.1"

    // Took Versions
    const val JAVA_VERSION = 23
    val JAVA_TARGET = JavaVersion.VERSION_23
    const val KOTLIN_VERSION = "2.2.10"
    const val GSON_VERSION = "2.13.1"
    const val ONNX_RUNTIME_VERSION = "1.23.2"
    const val HUGGING_FACE_TOKENIZERS_VERSION = "0.36.0"

    // 3rd Party Dependencies ( HTTP Client )
    const val KTOR_VERSION = "3.2.3"
}
