package com.onyx.exception

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
class InvalidIdentifierException @JvmOverloads constructor(message: String? = "") : OnyxException(message) {

    companion object {
        @JvmField val IDENTIFIER_MISSING = "Entity is missing primary key"
        @JvmField val IDENTIFIER_TYPE = "Entity identifier type is invalid"
        @JvmField val INVALID_GENERATOR = "Invalid generator for declared type"
    }
}
