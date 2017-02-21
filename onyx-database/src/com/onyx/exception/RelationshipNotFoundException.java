package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/1/15.
 *
 */
public class RelationshipNotFoundException extends EntityException
{
    public static final String RELATIONSHIP_NOT_FOUND = "Relationship not found: ";

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    public RelationshipNotFoundException(String message, String relationship, String className)
    {
        super(message + relationship + " for class " + className);
    }

    @SuppressWarnings("unused")
    public RelationshipNotFoundException()
    {
        super();
    }
}
