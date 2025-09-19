package com.onyx.cloud.impl

import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.OnyxConfig
import com.onyx.cloud.api.OnyxFacade

/** Default facade implementation. */
object OnyxFacadeImpl : OnyxFacade {
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

    @Suppress("UNCHECKED_CAST")
    override fun <Schema : Any> init(config: OnyxConfig?): IOnyxDatabase<Schema> =
        OnyxClient(
            baseUrl = config?.baseUrl ?: "https://api.onyx.dev",
            databaseId = config?.databaseId ?: System.getenv("ONYX_DATABASE_ID"),
            apiKey = config?.apiKey ?: System.getenv("ONYX_API_KEY"),
            apiSecret = config?.apiSecret ?: System.getenv("ONYX_API_SECRET")
        ) as IOnyxDatabase<Schema>

    override fun clearCacheConfig() {}
}