package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Attribute type does not match value
 */
class AttributeTypeMismatchException(message: String, expectedClass: Class<*>, actualClass: Class<*>, attribute: String) : OnyxException(message + expectedClass.name + " actual " + actualClass.name + " for attribute " + attribute) {

    var attribute: String = attribute

    companion object {
        @JvmField val ATTRIBUTE_TYPE_MISMATCH = "Attribute type mismatch, expecting "
    }
}
