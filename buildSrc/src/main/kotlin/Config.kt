import org.gradle.api.JavaVersion

object Config {
    // Onyx Version
    const val ONYX_VERSION = "3.6.3"

    // Took Versions
    const val JAVA_VERSION = 21
    val JAVA_TARGET = JavaVersion.VERSION_21
    const val KOTLIN_VERSION = "2.2.10"
    const val GSON_VERSION = "2.13.1"

    // 3rd Party Dependencies ( HTTP Client )
    const val KTOR_VERSION = "3.2.3"
}