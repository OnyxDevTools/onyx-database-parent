package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/6/14.
 *
 * Entity type does not fit expected type
 */
public class EntityTypeMatchException extends EntityException {

    public static final String ATTRIBUTE_TYPE_IS_NOT_SUPPORTED = "Attribute type is not supported";

    /**
     * Constructor
     *
     * @param message Error message
     */
    public EntityTypeMatchException(String message)
    {
        super(message);
    }

    @SuppressWarnings("unused")
    public EntityTypeMatchException()
    {
        super();
    }
}
