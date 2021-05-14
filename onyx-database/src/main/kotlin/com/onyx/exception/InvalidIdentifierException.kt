package com.onyx.exception

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
class InvalidIdentifierException @JvmOverloads constructor(message: String? = "") : OnyxException(message) {

    companion object {
        const val IDENTIFIER_MISSING = "Entity is missing primary key"
        const val IDENTIFIER_TYPE = "Entity identifier type is invalid"
        const val INVALID_GENERATOR = "Invalid generator for declared type"
    }
}
