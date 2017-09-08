package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Attribute type does not match value
 */
class AttributeTypeMismatchException @JvmOverloads constructor(message: String? = "", expectedClass: Class<*>? = null, actualClass: Class<*>? = null, val attribute: String? = "") : OnyxException(message + expectedClass?.name + " actual " + actualClass?.name + " for attribute " + attribute) {

    companion object {
        @JvmField val ATTRIBUTE_TYPE_MISMATCH = "Attribute type mismatch, expecting "
    }
}
