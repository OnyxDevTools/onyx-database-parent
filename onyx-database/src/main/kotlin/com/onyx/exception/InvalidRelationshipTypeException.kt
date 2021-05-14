package com.onyx.exception

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
class InvalidRelationshipTypeException : OnyxException {

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    @JvmOverloads
    constructor(message: String? = "") : super(message)

    /**
     * Constructor with message and cause
     *
     * @param message Error message
     * @param cause Root cause
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    companion object {
        const val INVERSE_RELATIONSHIP_INVALID = "Relationship inverse is invalid"
        const val INVERSE_RELATIONSHIP_MISMATCH = "Relationship inverse type does not match declared type"
        const val CANNOT_UPDATE_RELATIONSHIP = "Cannot update relationship.  You are attempting to change a to many relationship to a to many."
    }
}
