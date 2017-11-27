package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Value exceeds maximum size
 */
class AttributeSizeException @JvmOverloads constructor(message: String? = "", val attribute: String? = "") : OnyxException(message + " : " + attribute) {

    companion object {
        @JvmField val ATTRIBUTE_SIZE_EXCEPTION = "Attribute size exceeds maximum length"
    }
}
