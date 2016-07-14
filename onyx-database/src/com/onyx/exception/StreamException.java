package com.onyx.exception;

/**
 * Exception thrown when the persistence manager is unable to instantiate the aggregator
 */
public class StreamException extends EntityException
{
    public static final String CANNOT_INSTANTIATE_STREAM = "Unable to instantiate stream.  Define a valid constructor.";
    public static final String UNSUPPORTED_FUNCTION = "Unable to instantiate stream.  This function is unsupported.  Use PersistenceManager#stream(Query, Class) instead.";
    public static final String UNSUPPORTED_FUNCTION_ALTERNATIVE = "Unable to instantiate stream.  This function is unsupported.";

    /**
     * Default Constructor
     */
    public StreamException()
    {

    }

    /**
     * Default constructor with message
     *
     * @param message Message associated with exception
     */
    public StreamException(String message)
    {
        super(message);
    }
}
