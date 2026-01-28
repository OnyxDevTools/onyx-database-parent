@file:Suppress("unused")

package com.onyx.cloud.impl

import com.onyx.cloud.api.OnyxConfig
import com.onyx.cloud.extensions.fromJson
import java.io.File

/**
 * Resolves Onyx configuration using a chain of credential sources.
 *
 * Resolution order:
 * 1. Explicit config values passed to [resolve]
 * 2. Environment variables
 * 3. Config file at path specified by `ONYX_CONFIG_PATH` environment variable
 * 4. Project config file at `./config/onyx-database.json`
 * 5. Project config file at `./onyx-database.json`
 * 6. Home profile at `~/.onyx/config.json`
 *
 * Values from earlier sources take precedence over later sources.
 */
object ConfigResolver {

    private const val DEFAULT_BASE_URL = "https://api.onyx.dev"
    private const val DEFAULT_AI_BASE_URL = "https://ai.onyx.dev"
    private const val DEFAULT_MODEL = "onyx"

    // Environment variable names
    private const val ENV_DATABASE_ID = "ONYX_DATABASE_ID"
    private const val ENV_BASE_URL = "ONYX_DATABASE_BASE_URL"
    private const val ENV_AI_BASE_URL = "ONYX_AI_BASE_URL"
    private const val ENV_DEFAULT_MODEL = "ONYX_DEFAULT_MODEL"
    private const val ENV_API_KEY = "ONYX_DATABASE_API_KEY"
    private const val ENV_API_SECRET = "ONYX_DATABASE_API_SECRET"
    private const val ENV_CONFIG_PATH = "ONYX_CONFIG_PATH"
    private const val ENV_DEBUG = "ONYX_DEBUG"
    private const val ENV_PARTITION = "ONYX_PARTITION"

    // Legacy environment variable names (for backward compatibility)
    private const val ENV_API_KEY_LEGACY = "ONYX_API_KEY"
    private const val ENV_API_SECRET_LEGACY = "ONYX_API_SECRET"

    /**
     * JSON structure for config files.
     */
    private data class ConfigFile(
        val databaseId: String? = null,
        val baseUrl: String? = null,
        val aiBaseUrl: String? = null,
        val defaultModel: String? = null,
        val apiKey: String? = null,
        val apiSecret: String? = null,
        val partition: String? = null
    )

    /**
     * Holder for resolved configuration with source tracking for debug logging.
     */
    data class ResolvedConfig(
        val baseUrl: String,
        val aiBaseUrl: String,
        val defaultModel: String,
        val databaseId: String,
        val apiKey: String,
        val apiSecret: String,
        val partition: String?,
        val requestLoggingEnabled: Boolean,
        val responseLoggingEnabled: Boolean,
        val sources: Map<String, String> = emptyMap()
    )

    /**
     * Resolves the full configuration using the chain of credential sources.
     *
     * @param explicitConfig Optional explicit configuration that takes highest priority.
     * @return Resolved configuration with all values populated.
     * @throws IllegalStateException if required values cannot be resolved.
     */
    fun resolve(explicitConfig: OnyxConfig?): ResolvedConfig {
        val sources = mutableMapOf<String, String>()
        val debugEnabled = isDebugEnabled()

        // Load config files (lower priority sources first, then override)
        val fileConfigs = loadConfigFiles()

        // Resolve each value using the chain
        val baseUrl = resolveValue(
            "baseUrl",
            explicitConfig?.baseUrl,
            getEnv(ENV_BASE_URL),
            fileConfigs.mapNotNull { it.baseUrl }.firstOrNull(),
            DEFAULT_BASE_URL,
            sources
        )

        val aiBaseUrl = resolveValue(
            "aiBaseUrl",
            explicitConfig?.aiBaseUrl,
            getEnv(ENV_AI_BASE_URL),
            fileConfigs.mapNotNull { it.aiBaseUrl }.firstOrNull(),
            DEFAULT_AI_BASE_URL,
            sources
        )

        val defaultModel = resolveValue(
            "defaultModel",
            explicitConfig?.defaultModel,
            getEnv(ENV_DEFAULT_MODEL),
            fileConfigs.mapNotNull { it.defaultModel }.firstOrNull(),
            DEFAULT_MODEL,
            sources
        )

        val databaseId = resolveValue(
            "databaseId",
            explicitConfig?.databaseId,
            getEnv(ENV_DATABASE_ID),
            fileConfigs.mapNotNull { it.databaseId }.firstOrNull(),
            null,
            sources
        ) ?: throw IllegalStateException(
            "Database ID is required. Set ONYX_DATABASE_ID environment variable, " +
                "provide it in config, or pass it explicitly."
        )

        val apiKey = resolveValue(
            "apiKey",
            explicitConfig?.apiKey,
            getEnv(ENV_API_KEY) ?: getEnv(ENV_API_KEY_LEGACY),
            fileConfigs.mapNotNull { it.apiKey }.firstOrNull(),
            null,
            sources
        ) ?: throw IllegalStateException(
            "API key is required. Set ONYX_DATABASE_API_KEY environment variable, " +
                "provide it in config, or pass it explicitly."
        )

        val apiSecret = resolveValue(
            "apiSecret",
            explicitConfig?.apiSecret,
            getEnv(ENV_API_SECRET) ?: getEnv(ENV_API_SECRET_LEGACY),
            fileConfigs.mapNotNull { it.apiSecret }.firstOrNull(),
            null,
            sources
        ) ?: throw IllegalStateException(
            "API secret is required. Set ONYX_DATABASE_API_SECRET environment variable, " +
                "provide it in config, or pass it explicitly."
        )

        val partition = resolveValue(
            "partition",
            explicitConfig?.partition,
            getEnv(ENV_PARTITION),
            fileConfigs.mapNotNull { it.partition }.firstOrNull(),
            null,
            sources
        )

        // Logging is enabled via explicit config OR if ONYX_DEBUG is set
        val loggingEnabled = debugEnabled ||
            (explicitConfig?.requestLoggingEnabled == true) ||
            (explicitConfig?.responseLoggingEnabled == true)

        if (debugEnabled) {
            logResolvedSources(sources)
        }

        return ResolvedConfig(
            baseUrl = baseUrl ?: "https://api.onyx.dev",
            aiBaseUrl = aiBaseUrl ?: "https://ai.onyx.dev",
            defaultModel = defaultModel ?: "onyx",
            databaseId = databaseId,
            apiKey = apiKey,
            apiSecret = apiSecret,
            partition = partition,
            requestLoggingEnabled = explicitConfig?.requestLoggingEnabled ?: debugEnabled,
            responseLoggingEnabled = explicitConfig?.responseLoggingEnabled ?: debugEnabled,
            sources = sources
        )
    }

    /**
     * Checks if ONYX_DEBUG is enabled.
     */
    fun isDebugEnabled(): Boolean {
        val debugValue = getEnv(ENV_DEBUG)?.lowercase()
        return debugValue == "true" || debugValue == "1" || debugValue == "yes"
    }

    /**
     * Resolves a single configuration value using the priority chain.
     */
    private fun resolveValue(
        name: String,
        explicit: String?,
        envValue: String?,
        fileValue: String?,
        defaultValue: String?,
        sources: MutableMap<String, String>
    ): String? {
        return when {
            !explicit.isNullOrBlank() -> {
                sources[name] = "explicit"
                explicit
            }
            !envValue.isNullOrBlank() -> {
                sources[name] = "environment"
                envValue
            }
            !fileValue.isNullOrBlank() -> {
                sources[name] = "config_file"
                fileValue
            }
            defaultValue != null -> {
                sources[name] = "default"
                defaultValue
            }
            else -> null
        }
    }

    /**
     * Loads configuration from all possible config file locations.
     * Returns list in priority order (highest priority first).
     */
    private fun loadConfigFiles(): List<ConfigFile> {
        val configs = mutableListOf<ConfigFile>()

        // 1. ONYX_CONFIG_PATH environment variable
        getEnv(ENV_CONFIG_PATH)?.let { path ->
            loadConfigFile(File(path))?.let { configs.add(it) }
        }

        // 2. Project config: ./config/onyx-database.json
        loadConfigFile(File("./config/onyx-database.json"))?.let { configs.add(it) }

        // 3. Project config: ./onyx-database.json
        loadConfigFile(File("./onyx-database.json"))?.let { configs.add(it) }

        // 4. Home profile: ~/.onyx/config.json
        val homeDir = System.getProperty("user.home")
        loadConfigFile(File(homeDir, ".onyx/config.json"))?.let { configs.add(it) }

        return configs
    }

    /**
     * Loads and parses a single config file.
     */
    private fun loadConfigFile(file: File): ConfigFile? {
        return try {
            if (file.exists() && file.isFile) {
                val content = file.readText()
                content.fromJson<ConfigFile>()
            } else {
                null
            }
        } catch (e: Exception) {
            // Silently ignore malformed config files
            null
        }
    }

    /**
     * Gets an environment variable value, returning null for blank values.
     */
    private fun getEnv(name: String): String? {
        return System.getenv(name)?.takeIf { it.isNotBlank() }
    }

    /**
     * Logs which sources were used for each configuration value (when debug is enabled).
     */
    private fun logResolvedSources(sources: Map<String, String>) {
        println("OnyxClient Configuration Sources:")
        sources.forEach { (key, source) ->
            println("  $key: $source")
        }
    }
}
