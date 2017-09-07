package com.onyx.exception

/**
 * Created by timothy.osborn on 3/20/15.
 * Cannot hydrate a relationship
 */
class RelationshipHydrationException(relationship: String, className: String, id: Any) : OnyxException(FAILURE_TO_HYDRATE + relationship + " for class " + className + " with id " + id.toString()) {

    companion object {
        @JvmField val FAILURE_TO_HYDRATE = "Failure to hydrate relationship: "
    }
}
