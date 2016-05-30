package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/12/14.
 */
public class InvalidRelationshipTypeException extends EntityException
{
    public static final String INVERSE_RELATIONSHIP_INVALID = "Relationship inverse is invalid";
    public static final String INVERSE_RELATIONSHIP_MISMATCH = "Relationship inverse type does not match declared type";
    public static final String CANNOT_UPDATE_RELATIONSHIP = "Cannot update relationship.  You are attempting to change a to many relationship to a to many.";
    public static final String INVALID_TO_MANY_RELATIONSHIP = "Relationship type is invalid.  You must declare the relationship as a List type.";
    public static final String INVALID_TO_ONE_RELATIONSHIP = "Relationship type is invalid.  You must declare the relationship as an Entity type.";

    /**
     * Constructor with message
     *
     * @param message
     */
    public InvalidRelationshipTypeException(String message)
    {
        super(message);
    }

    /**
     * Constructor with message and cause
     *
     * @param message
     * @param cause
     */
    public InvalidRelationshipTypeException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public InvalidRelationshipTypeException()
    {
        super();
    }
}
