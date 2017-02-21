package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Value exceeds maximum size
 */
public class AttributeSizeException extends EntityException
{
    public static final String ATTRIBUTE_SIZE_EXCEPTION = "Attribute size exceeds maximum length";

    protected String attribute;

    @SuppressWarnings("unused")
    public AttributeSizeException()
    {

    }

    /**
     * Constructor with message and attribute
     *
     * @param message Exception message
     * @param attribute Attribute caused exception
     */
    public AttributeSizeException(String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }
}
