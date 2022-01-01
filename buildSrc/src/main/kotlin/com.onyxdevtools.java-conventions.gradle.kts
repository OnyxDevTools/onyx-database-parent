@file:Suppress("UnstableApiUsage")

import java.net.URI
import java.util.*

/*
 * This file was generated by the Gradle 'init' task.
 */
plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

group = "com.onyxdevtools"
version = "2.2.2"
java.sourceCompatibility = JavaVersion.VERSION_1_8

java {
    withSourcesJar()
}

fun base64Decode(prop: String): String? {
    return project.findProperty(prop)?.let {
        String(Base64.getDecoder().decode(it.toString())).trim()
    }
}

signing {
    useInMemoryPgpKeys(base64Decode("signing.secretKey"), project.findProperty("signing.password") as String)
    sign(publishing.publications)
}

publishing {

    repositories {
        maven {
            url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.property("ossrhUsername") as String
                password = project.property("ossrhPassword") as String
            }
        }
    }

    publications {
        publications.create<MavenPublication>("maven") {
            from(components["java"])
            val pub = this
            pom {
                name.set(pub.artifactId)
                description.set("Onyx Database is a graph database that is written in Kotlin and supports Java and Android.  It is designed to be lightweight and easy to use.  Features include in memory database, embedded, and remote server.  It leverages its own ORM and storage.")
                url.set("http://onyx.dev")
                licenses {
                    license {
                        name.set("Free Software Foundation's GNU AGPL v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.en.html")
                    }
                }
                developers {
                    developer {
                        id.set("tosborn")
                        name.set("Tim Osborn")
                        email.set("tosborn@onyx.dev")
                    }
                }
                scm {
                    url.set("https://github.com/OnyxDevTools/onyx-database-parent")
                }
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}
