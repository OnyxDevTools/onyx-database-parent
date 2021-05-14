package com.onyx.exception

/**
 * Created by timothy.osborn on 12/6/14.
 *
 * Entity type does not fit expected type
 */
class EntityTypeMatchException @JvmOverloads constructor(message: String? = "") : OnyxException(message) {

    companion object {
        const val ATTRIBUTE_TYPE_IS_NOT_SUPPORTED = "Attribute type is not supported"
    }
}
