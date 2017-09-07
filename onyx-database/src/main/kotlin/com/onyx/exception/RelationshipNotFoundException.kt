package com.onyx.exception

/**
 * Created by timothy.osborn on 1/1/15.
 *
 */
class RelationshipNotFoundException(message: String, relationship: String, className: String) : OnyxException(message + relationship + " for class " + className) {

    companion object {
        @JvmField val RELATIONSHIP_NOT_FOUND = "Relationship not found: "
    }
}
