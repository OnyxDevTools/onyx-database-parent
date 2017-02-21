package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/12/14.
 *
 * Needs a valid constructor
 */
public class InvalidConstructorException extends EntityException
{
    public static final String CONSTRUCTOR_NOT_FOUND = "No constructor found for entity";
    public static final String MISSING_ENTITY_TYPE = "No Entity Class defined";
    /**
     * Constructor with message and cause
     *
     * @param message
     * @param cause
     */
    public InvalidConstructorException(String message, Throwable cause)
    {
        super(message, cause);
    }

    @SuppressWarnings("unused")
    public InvalidConstructorException()
    {
        super();
    }
}
