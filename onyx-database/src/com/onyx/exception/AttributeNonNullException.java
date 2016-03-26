package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 */
public class AttributeNonNullException extends EntityException
{
    public static final String ATTRIBUTE_NULL_EXCEPTION = "Attribute must not be null";

    protected String attribute;

    /**
     * Constructor with message and attribute
     *
     * @param message
     * @param attribute
     */
    public AttributeNonNullException(String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }

    public AttributeNonNullException()
    {

    }
}
