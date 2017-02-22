package com.onyx.exception;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 */
public class InvalidDataTypeForOperator extends EntityException
{

    public static final String INVALID_DATA_TYPE_FOR_OPERATOR = "Invalid Data Type to be used for comparison operator";

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    public InvalidDataTypeForOperator(@SuppressWarnings("SameParameterValue") String message)
    {
        super(message);
    }

    @SuppressWarnings("unused")
    public InvalidDataTypeForOperator()
    {
        super();
    }
}
