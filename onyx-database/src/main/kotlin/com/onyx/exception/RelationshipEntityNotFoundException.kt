package com.onyx.exception

/**
 * Created by timothy.osborn on 1/1/15.
 *
 */
class RelationshipEntityNotFoundException @JvmOverloads constructor(message: String? = RELATIONSHIP_NOT_FOUND, relationship: String? = "", className: String? = "") : OnyxException(
    "$message$relationship for class $className"
) {

    companion object {
        const val RELATIONSHIP_NOT_FOUND = "Relationship entity not found: "
    }
}
