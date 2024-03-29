package com.onyx.exception

/**
 * Created by timothy.osborn on 1/21/15.
 *
 */
class IdentifierRequiredException @JvmOverloads constructor(message: String? = "", var attribute: String? = "") : OnyxException(
    "$message : $attribute"
) {
    companion object {
        const val IDENTIFIER_REQUIRED_EXCEPTION = "Identifier key is required"
    }
}
