package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
public class InvalidIndexException extends EntityException
{
    public static final String INDEX_MISSING_ATTRIBUTE = "Index is missing attribute annotation";
    public static final String INDEX_MISSING_FIELD = "Index is missing attribute";

    /**
     * Constructor with message
     *
     * @param message Error message
     */
    public InvalidIndexException(String message)
    {
        super(message);
    }

    @SuppressWarnings("unused")
    public InvalidIndexException()
    {
        super();
    }
}
