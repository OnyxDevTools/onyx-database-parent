package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 */
public class AttributeSizeException extends EntityException
{
    public static final String ATTRIBUTE_SIZE_EXCEPTION = "Attribute size exceeds maximum length";

    protected String attribute;

    public AttributeSizeException()
    {

    }

    /**
     * Constructor with message and attribute
     *
     * @param message
     * @param attribute
     */
    public AttributeSizeException(String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }
}
