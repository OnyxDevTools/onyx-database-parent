package com.onyx.exception

/**
 * Created by timothy.osborn on 12/6/14.
 *
 * Class that is trying to work on is not a persistable type
 */
class EntityClassNotFoundException(message: String) : OnyxException(message) {

    private var entityClassName: String? = null

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    constructor(message: String, entityType: Class<*>) : this(message + " for class " + entityType.name) {
        entityClassName = entityType.name
    }

    companion object {

        @JvmField val RELATIONSHIP_ENTITY_PERSISTED_NOT_FOUND = "Relationship type does not implement IManagedEntity"
        @JvmField val RELATIONSHIP_ENTITY_BASE_NOT_FOUND = "Relationship type does not extend from ManagedEntity"
        @JvmField val RELATIONSHIP_ENTITY_NOT_FOUND = "Relationship type does not have entity annotation"
        @JvmField val ENTITY_NOT_FOUND = "Entity is not able to persist because entity annotation does not exist"
        @JvmField val PERSISTED_NOT_FOUND = "Entity is not able to persist because entity does not implement IManagedEntity"
        @JvmField val EXTENSION_NOT_FOUND = "Entity is not able to persist because entity does not extend from ManagedEntity"
        @JvmField val TO_MANY_INVALID_TYPE = "To Many relationship must by type List.class"
    }
}
