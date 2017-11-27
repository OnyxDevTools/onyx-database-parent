package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Null check exception
 */
class AttributeNonNullException @JvmOverloads constructor(message: String? = "", attribute: String? = "") : OnyxException(message + " : " + attribute) {

    companion object {
        @JvmField val ATTRIBUTE_NULL_EXCEPTION = "Attribute must not be null"
    }
}
