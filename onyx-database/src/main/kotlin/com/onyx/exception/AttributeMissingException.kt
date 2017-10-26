package com.onyx.exception

/**
 * Created by timothy.osborn on 12/4/14.
 *
 * Trying to save of fetch on an attribute that does not exist on the entity or is
 * not specified as an entity
 *
 */
class AttributeMissingException @JvmOverloads constructor(message: String? = null) : OnyxException(message ?: "") {

    companion object {
        @JvmField val ENTITY_MISSING_ATTRIBUTE = "Entity attribute does not exist"
    }
}
