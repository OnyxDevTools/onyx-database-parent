package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 */
public class AttributeTypeMismatchException extends EntityException
{
    public static final String ATTRIBUTE_TYPE_MISMATCH = "Attribute type mismatch, expecting ";

    protected String attribute;
    protected Class expectedClass;
    protected Class actualClass;

    /**
     * Constructor with message and attribute
     *
     * @param message
     * @param attribute
     */
    public AttributeTypeMismatchException(String message, Class expectedClass, Class actualClass, String attribute)
    {
        super(message + expectedClass.getName() + " actual " + actualClass.getName() + " for attribute " + attribute);
        this.attribute = attribute;
        this.expectedClass = expectedClass;
        this.actualClass = actualClass;
    }

    public AttributeTypeMismatchException()
    {

    }
}
