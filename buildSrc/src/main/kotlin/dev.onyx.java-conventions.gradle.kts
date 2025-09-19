@file:Suppress("UnstableApiUsage")

import java.util.*
import java.net.HttpURLConnection
import java.net.URL
import org.gradle.api.Project

plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenLocal()
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

group = "dev.onyx"
version = Config.ONYX_VERSION
java.sourceCompatibility = Config.JAVA_TARGET
java.targetCompatibility = Config.JAVA_TARGET

java { withSourcesJar() }

private fun toEnvKey(propertyName: String): String =
    propertyName.replace(".", "_")
        .replace(Regex("([a-z])([A-Z])"), "\$1_\$2")
        .uppercase(Locale.US)

private fun Project.findOptionalProperty(vararg keys: String): String? =
    keys.asSequence()
        .mapNotNull { key ->
            findProperty(key)?.toString()?.ifBlank { null }
                ?: System.getenv(toEnvKey(key))?.ifBlank { null }
        }
        .firstOrNull()

private fun Project.findOptionalBase64Property(vararg keys: String): String? =
    findOptionalProperty(*keys)?.let { String(Base64.getDecoder().decode(it)).trim() }

signing {
    val secretKey = project.findOptionalBase64Property("signing.secretKey", "maven.signing.key")
    val password = project.findOptionalProperty("signing.password", "maven.signing.password")
    if (secretKey != null && password != null) {
        useInMemoryPgpKeys(secretKey, password)
        sign(publishing.publications)
    }
}

publishing {
    repositories {
        maven {
            name = "Sonatype"
            // Use Central Portal services (OSSRH staging API compat + Central snapshots)
            val releasesUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
            val isSnapshot = version.toString().endsWith("SNAPSHOT")
            url = if (isSnapshot) snapshotsUrl else releasesUrl

            // IMPORTANT: these must be the Central Portal user token creds
            val username = project.findOptionalProperty("maven.sonatype.username")
            val password = project.findOptionalProperty("maven.sonatype.password")

            if (username != null && password != null) {
                credentials {
                    this.username = username
                    this.password = password
                }
            }
        }
    }

    publications {
        publications.create<MavenPublication>("maven") {
            from(components["java"])
            val pub = this
            pom {
                name.set(pub.artifactId)
                description.set("Onyx Cloud Services develops the Onyx Database, a Kotlin-first graph database for JVM and Android applications with in-memory, embedded, and remote deployment options.")
                url.set("https://onyx.dev")
                organization {
                    name.set("Onyx Cloud Services")
                    url.set("https://onyx.dev")
                }
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
                        organization.set("Onyx Cloud Services")
                        organizationUrl.set("https://onyx.dev")
                    }
                }
                scm { url.set("https://github.com/OnyxDevTools/onyx-database-parent") }
            }
        }
    }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<GenerateModuleMetadata> { suppressedValidationErrors.add("enforced-platform") }

// ---- Central Portal finalize (required for Gradle's maven-publish via OSSRH Staging API) ----
// See docs: replace s01 with ossrh-staging-api + call /manual/upload after PUTs so the portal sees it & publishes.
// ---- Central Portal finalize (runs ONCE after all modules publish) ----
// Replaces per-module finalize; only runs on the root project after every subproject has published.
// ---- Central Portal finalize (required for Gradle's maven-publish via OSSRH Staging API) ----
// See docs: replace s01 with ossrh-staging-api + call /manual/upload after PUTs so the portal sees it & publishes.
val centralNamespace = "dev.onyx" // your Central namespace (matches your group)

fun bearer(user: String, pass: String): String =
    Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))

// Register the same task name, but make it finalize ONLY after *all* Sonatype publish tasks are done.
val centralFinalize by tasks.register("centralFinalize") {
    group = "publishing"
    description = "Finalize upload to Central Portal (auto-publish)."

    // Keep the original dependency so it's still a drop-in for single-module builds
    dependsOn(tasks.named("publishMavenPublicationToSonatypeRepository"))

    doLast {
        val user = project.findOptionalProperty("maven.sonatype.username")
            ?: error("Missing maven.sonatype.username (Portal token username)")
        val pass = project.findOptionalProperty("maven.sonatype.password")
            ?: error("Missing maven.sonatype.password (Portal token password)")

        // Only the last module to finish will actually call the finalize API.
        // We compute "am I last?" by checking if there are any *other* Sonatype publish tasks still scheduled/running.
        val remaining = gradle.taskGraph.allTasks.filter {
            it !== this && it.name.startsWith("publish") && it.name.endsWith("ToSonatypeRepository") && !it.state.executed
        }
        if (remaining.isNotEmpty()) {
            println("Central finalize: waiting (${remaining.size} remaining Sonatype publish task(s))")
            return@doLast
        }

        val url = URL(
            "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$centralNamespace?publishing_type=automatic"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${bearer(user, pass)}")
            doOutput = true
            useCaches = false
        }
        conn.outputStream.use { } // no body
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            // Treat already-released repos as success to keep multi-module builds green
            if (code == 400 && (err.contains("state released", true) || err.contains("must be dropped", true))) {
                println("Central finalize: repository already released (skipping).")
            } else {
                error("Central finalize failed ($code): ${if (err.isBlank()) "no message" else err}")
            }
        } else {
            println("Central finalize: OK ($code)")
        }
    }
}

// Keep your existing chaining so this still runs after 'publish'
tasks.named("publish") { finalizedBy(centralFinalize) }

// Ensure this finalize runs only once at the end of the whole build when multiple modules publish:
// After all projects are evaluated, make centralFinalize depend on *all* Sonatype publish tasks in all subprojects.
gradle.projectsEvaluated {
    val allSonatypePublishes = rootProject.subprojects.flatMap { sp ->
        sp.tasks.matching { it.name.startsWith("publish") && it.name.endsWith("ToSonatypeRepository") }.toList()
    }
    // Add our own project's publish task too (if not already captured)
    val here = tasks.matching { it.name.startsWith("publish") && it.name.endsWith("ToSonatypeRepository") }.toList()
    val allTargets = (allSonatypePublishes + here).distinct()

    // Make this project's centralFinalize wait for *every* module’s Sonatype publish task
    tasks.named("centralFinalize") {
        dependsOn(allTargets)
    }
}
