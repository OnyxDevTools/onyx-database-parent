package com.onyx.exception

/**
 * Created by timothy.osborn on 12/4/14.
 *
 * Trying to save of fetch on an attribute that does not exist on the entity or is
 * not specified as an entity
 *
 */
class AttributeMissingException : OnyxException {

    /**
     * Constructor with message and cause
     *
     * @param message Error message
     * @param cause Root cause
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    @JvmOverloads
    constructor(message: String? = null) : super(message ?: "")

    companion object {

        @JvmField val ENTITY_MISSING_ATTRIBUTE = "Entity attribute does not exist"
        @JvmField val ILLEGAL_ACCESS_ATTRIBUTE = "Illegal access for attribute"
    }
}
