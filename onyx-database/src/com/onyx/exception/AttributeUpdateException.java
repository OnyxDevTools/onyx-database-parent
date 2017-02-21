package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 *
 */
public class AttributeUpdateException extends EntityException
{
    public static final String ATTRIBUTE_UPDATE_IDENTIFIER = "Cannot update the entity's identifier";

    protected String attribute;

    /**
     * Constructor with message and attribute
     *
     * @param message Exception message
     * @param attribute Attribute causing exception
     */
    public AttributeUpdateException(String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }

    @SuppressWarnings("unused")
    public AttributeUpdateException()
    {
        super();
    }
}
