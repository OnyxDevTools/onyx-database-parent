package com.onyx.exception

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
class InvalidConstructorException (message: String, cause: Throwable) : OnyxException(message, cause) {
    companion object {
        @JvmField val CONSTRUCTOR_NOT_FOUND = "No constructor found for entity"
        @JvmField val MISSING_ENTITY_TYPE = "No Entity Class defined"
    }
}
