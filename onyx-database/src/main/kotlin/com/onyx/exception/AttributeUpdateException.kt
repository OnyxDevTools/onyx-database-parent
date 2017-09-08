package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 */
class AttributeUpdateException @JvmOverloads constructor(message: String? = "", attribute: String? = "") : OnyxException(message + " : " + attribute) {

    companion object {
        @JvmField val ATTRIBUTE_UPDATE_IDENTIFIER = "Cannot update the entity's identifier"
    }
}
