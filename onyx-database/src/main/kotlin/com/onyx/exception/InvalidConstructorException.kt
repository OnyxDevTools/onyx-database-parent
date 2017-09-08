package com.onyx.exception

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
class InvalidConstructorException @JvmOverloads constructor(message: String? = "", cause: Throwable? = null) : OnyxException(message, cause) {

    companion object {
        @JvmField val CONSTRUCTOR_NOT_FOUND = "No constructor found for entity"
    }
}
