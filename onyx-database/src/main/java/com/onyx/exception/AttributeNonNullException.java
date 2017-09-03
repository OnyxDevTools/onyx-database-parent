package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Null check exception
 */
public class AttributeNonNullException extends EntityException
{
    public static final String ATTRIBUTE_NULL_EXCEPTION = "Attribute must not be null";

    @SuppressWarnings("unused")
    protected String attribute;

    /**
     * Constructor with message and attribute
     *
     * @param message Exception message
     * @param attribute that failed null check
     */
    public AttributeNonNullException(@SuppressWarnings("SameParameterValue") String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }

    @SuppressWarnings("unused")
    public AttributeNonNullException()
    {

    }
}
