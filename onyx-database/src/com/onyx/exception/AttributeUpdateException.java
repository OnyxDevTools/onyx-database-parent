package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/21/15.
 */
public class AttributeUpdateException extends EntityException
{
    public static final String ATTRIBUTE_UPDATE_IDENTIFIER = "Cannot update the entity's identifier";
    public static final String UNKNOWN = "Unknown exception when running update query";

    protected String attribute;

    /**
     * Constructor with message and attribute
     *
     * @param message
     * @param attribute
     */
    public AttributeUpdateException(String message, String attribute)
    {
        super(message + " : " + attribute);
        this.attribute = attribute;
    }

    /**
     * Constructor with message and throwable
     *
     * @param message
     * @param t
     */
    public AttributeUpdateException(String message, Throwable t)
    {
        super(message, t);
    }

    public AttributeUpdateException()
    {
        super();
    }
}
