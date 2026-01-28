package com.onyx.cloud.impl

import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.OnyxConfig
import com.onyx.cloud.api.OnyxFacade

/**
 * Default [OnyxFacade] implementation wiring up the HTTP client and configuration helpers.
 *
 * The facade exposes simple entry points for constructing [IOnyxDatabase] instances either from explicit
 * credentials or from a pre-built [OnyxConfig]. Configuration resolution follows the chain:
 *
 * 1. Explicit config values passed directly
 * 2. Environment variables (`ONYX_DATABASE_ID`, `ONYX_DATABASE_BASE_URL`, `ONYX_DATABASE_API_KEY`,
 *    `ONYX_DATABASE_API_SECRET`, `ONYX_AI_BASE_URL`, `ONYX_DEFAULT_MODEL`, `ONYX_PARTITION`)
 * 3. Config file at path specified by `ONYX_CONFIG_PATH` environment variable
 * 4. Project config file at `./config/onyx-database.json`
 * 5. Project config file at `./onyx-database.json`
 * 6. Home profile at `~/.onyx/config.json`
 *
 * Setting `ONYX_DEBUG=true` enables both request/response logging and logs which credential source was used.
 */
object OnyxFacadeImpl : OnyxFacade {

    @Volatile
    private var cachedConfig: ConfigResolver.ResolvedConfig? = null
    private val cacheLock = Any()

    /**
     * Creates an [IOnyxDatabase] using explicit connection parameters.
     *
     * @param baseUrl base URL of the Onyx API.
     * @param databaseId identifier of the target database.
     * @param apiKey public API key used for authentication.
     * @param apiSecret private API secret used for authentication.
     * @return a database client targeting the specified database instance.
     */
    override fun init(
        baseUrl: String,
        databaseId: String,
        apiKey: String,
        apiSecret: String
    ): IOnyxDatabase<Any> {
        val config = OnyxConfig(
            baseUrl = baseUrl,
            databaseId = databaseId,
            apiSecret = apiSecret,
            apiKey = apiKey
        )
        return init<Any>(config)
    }

    /**
     * Creates a type-safe [IOnyxDatabase] using an optional [OnyxConfig].
     *
     * Configuration is resolved using the following chain (first non-null wins):
     * 1. Explicit values from [config]
     * 2. Environment variables
     * 3. Config files (ONYX_CONFIG_PATH → ./config/onyx-database.json → ./onyx-database.json → ~/.onyx/config.json)
     * 4. Default values
     *
     * @param config optional connection configuration. When `null`, values are resolved from env/files.
     * @return a database client typed to [Schema].
     * @throws IllegalStateException if required values (databaseId, apiKey, apiSecret) cannot be resolved.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <Schema : Any> init(config: OnyxConfig?): IOnyxDatabase<Schema> {
        val resolved = synchronized(cacheLock) {
            // Use cached config if available and no explicit config provided
            if (config == null && cachedConfig != null) {
                cachedConfig!!
            } else {
                val newConfig = ConfigResolver.resolve(config)
                // Only cache when no explicit config is provided
                if (config == null) {
                    cachedConfig = newConfig
                }
                newConfig
            }
        }

        return OnyxClient(
            baseUrl = resolved.baseUrl,
            databaseId = resolved.databaseId,
            apiKey = resolved.apiKey,
            apiSecret = resolved.apiSecret,
            fetch = config?.fetch,
            defaultPartition = resolved.partition,
            requestLoggingEnabled = resolved.requestLoggingEnabled,
            responseLoggingEnabled = resolved.responseLoggingEnabled,
            ttl = config?.ttl,
            requestTimeoutMsOverride = config?.requestTimeoutMs,
            connectTimeoutMsOverride = config?.connectTimeoutMs,
            aiBaseUrl = resolved.aiBaseUrl,
            defaultModel = resolved.defaultModel
        ) as IOnyxDatabase<Schema>
    }

    /**
     * Clears any cached configuration. The next call to [init] will re-resolve all settings
     * from environment variables and config files.
     */
    override fun clearCacheConfig() {
        synchronized(cacheLock) {
            cachedConfig = null
        }
    }
}
