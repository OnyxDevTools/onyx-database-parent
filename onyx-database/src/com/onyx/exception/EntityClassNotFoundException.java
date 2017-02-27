package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/6/14.
 *
 * Class that is trying to work on is not a persistable type
 */
public class EntityClassNotFoundException extends EntityException {

    public static final String RELATIONSHIP_ENTITY_PERSISTED_NOT_FOUND = "Relationship type does not implement IManagedEntity";
    public static final String RELATIONSHIP_ENTITY_BASE_NOT_FOUND = "Relationship type does not extend from ManagedEntity";
    public static final String RELATIONSHIP_ENTITY_NOT_FOUND = "Relationship type does not have entity annotation";
    public static final String ENTITY_NOT_FOUND = "Entity is not able to persist because entity annotation does not exist";
    public static final String PERSISTED_NOT_FOUND = "Entity is not able to persist because entity does not implement IManagedEntity";
    public static final String EXTENSION_NOT_FOUND = "Entity is not able to persist because entity does not extend from ManagedEntity";
    public static final String TO_MANY_INVALID_TYPE = "To Many relationship must by type List.class";

    private String entityClassName = null;

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    public EntityClassNotFoundException(String message, Class entityType)
    {
        super(message + " for class " + entityType.getName());
        entityClassName = entityType.getName();
    }

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    public EntityClassNotFoundException(String message)
    {
        super(message);
    }

    @SuppressWarnings("unused")
    public String getEntityClassName() {
        return entityClassName;
    }
}
