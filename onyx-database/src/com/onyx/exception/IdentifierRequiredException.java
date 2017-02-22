package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 *
 */
public class IdentifierRequiredException extends EntityException
{
    public static final String IDENTIFIER_REQUIRED_EXCEPTION = "Identifier key is required";

    @SuppressWarnings("unused")
    protected String attribute;

    @SuppressWarnings("unused")
    public IdentifierRequiredException()
    {

    }

    /**
     * Constructor with message and attribute
     *
     * @param message Error message
     * @param attribute Attribute causing exception
     */
    public IdentifierRequiredException(@SuppressWarnings("SameParameterValue") String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }
}
