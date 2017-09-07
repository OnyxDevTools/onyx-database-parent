package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 */
class IdentifierRequiredException(message: String, var attribute: String) : OnyxException(message + " : " + attribute) {
    companion object {
        @JvmField val IDENTIFIER_REQUIRED_EXCEPTION = "Identifier key is required"
    }
}
