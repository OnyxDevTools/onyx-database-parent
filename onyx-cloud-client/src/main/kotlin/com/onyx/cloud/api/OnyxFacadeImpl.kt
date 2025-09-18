package com.onyx.cloud.api

/** Default facade implementation. */
object OnyxFacadeImpl : OnyxFacade {
    override fun <Schema : Any> init(config: OnyxConfig?): IOnyxDatabase<Schema> =
        throw NotImplementedError("Not implemented")

    override fun clearCacheConfig() {}
}
