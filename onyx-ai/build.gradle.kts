description = "com.onyxdevtools:onyx-ai"

plugins {
    kotlin("jvm") version Config.KOTLIN_VERSION
    id("com.onyxdevtools.java-conventions")
}

kotlin {
    jvmToolchain(17)
}