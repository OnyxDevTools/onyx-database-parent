package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 *
 * Attribute type does not match value
 */
public class AttributeTypeMismatchException extends EntityException
{
    public static final String ATTRIBUTE_TYPE_MISMATCH = "Attribute type mismatch, expecting ";

    @SuppressWarnings("WeakerAccess")
    protected String attribute;
    private Class expectedClass;
    private Class actualClass;

    /**
     * Constructor with message and attribute
     *
     * @param message Exception message
     * @param attribute Attribute causing exception
     */
    public AttributeTypeMismatchException(@SuppressWarnings("SameParameterValue") String message, Class expectedClass, Class actualClass, String attribute)
    {
        super(message + expectedClass.getName() + " actual " + actualClass.getName() + " for attribute " + attribute);
        this.attribute = attribute;
        this.expectedClass = expectedClass;
        this.actualClass = actualClass;
    }

    @SuppressWarnings("unused")
    public AttributeTypeMismatchException()
    {

    }

    @SuppressWarnings("unused")
    public String getAttribute() {
        return attribute;
    }

    @SuppressWarnings("unused")
    public Class getExpectedClass() {
        return expectedClass;
    }

    @SuppressWarnings("unused")
    public Class getActualClass() {
        return actualClass;
    }
}
