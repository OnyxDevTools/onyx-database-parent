package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 */
public class IdentifierRequiredException extends EntityException
{
    public static final String IDENTIFIER_REQUIRED_EXCEPTION = "Identifier value is required";

    protected String attribute;

    public IdentifierRequiredException()
    {

    }

    /**
     * Constructor with message and attribute
     *
     * @param message
     * @param attribute
     */
    public IdentifierRequiredException(String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }
}
