package com.onyx.cloud.impl

import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.OnyxConfig
import com.onyx.cloud.api.OnyxFacade

/** Default facade implementation. */
object OnyxFacadeImpl : OnyxFacade {
    override fun <Schema : Any> init(config: OnyxConfig?): IOnyxDatabase<Schema> =
        throw NotImplementedError("Not implemented")

    override fun clearCacheConfig() {}
}