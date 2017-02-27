package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
public class InvalidIdentifierException extends EntityException
{
    public static final String IDENTIFIER_MISSING = "Entity is missing primary key";
    public static final String IDENTIFIER_MISSING_ATTRIBUTE = "Entity identifier is missing Attribute annotation";
    public static final String IDENTIFIER_TYPE = "Entity identifier type is invalid";
    public static final String INVALID_GENERATOR = "Invalid generator for declared type";

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    public InvalidIdentifierException(String message)
    {
        super(message);
    }

    @SuppressWarnings("unused")
    public InvalidIdentifierException()
    {
        super();
    }
}
