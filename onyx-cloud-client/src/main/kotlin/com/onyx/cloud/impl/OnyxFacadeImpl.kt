package com.onyx.cloud.impl

import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.OnyxConfig
import com.onyx.cloud.api.OnyxFacade

/**
 * Default [OnyxFacade] implementation wiring up the HTTP client and configuration helpers.
 *
 * The facade exposes simple entry points for constructing [IOnyxDatabase] instances either from explicit
 * credentials or from a pre-built [OnyxConfig]. Missing configuration values fall back to environment
 * variables so that applications can be configured outside of code when desired.
 */
object OnyxFacadeImpl : OnyxFacade {
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
     * Missing configuration values fall back to the default API endpoint and the environment variables
     * `ONYX_DATABASE_ID`, `ONYX_API_KEY`, and `ONYX_API_SECRET` respectively.
     *
     * @param config optional connection configuration. When `null`, the defaults described above are used.
     * @return a database client typed to [Schema].
     */
    @Suppress("UNCHECKED_CAST")
    override fun <Schema : Any> init(config: OnyxConfig?): IOnyxDatabase<Schema> =
        OnyxClient(
            baseUrl = config?.baseUrl ?: "https://api.onyx.dev",
            databaseId = config?.databaseId ?: System.getenv("ONYX_DATABASE_ID"),
            apiKey = config?.apiKey ?: System.getenv("ONYX_API_KEY"),
            apiSecret = config?.apiSecret ?: System.getenv("ONYX_API_SECRET"),
            fetch = config?.fetch,
            defaultPartition = config?.partition,
            requestLoggingEnabled = config?.requestLoggingEnabled ?: false,
            responseLoggingEnabled = config?.responseLoggingEnabled ?: false,
            ttl = config?.ttl
        ) as IOnyxDatabase<Schema>

    /**
     * Clears any cached configuration or singletons held by the facade.
     *
     * The current implementation does not hold state, but the method remains for API compatibility.
     */
    override fun clearCacheConfig() {}
}