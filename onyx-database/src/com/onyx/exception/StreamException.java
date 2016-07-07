package com.onyx.exception;

/**
 * Exception thrown when the persistence manager is unable to instantiate the aggregator
 */
public class StreamException extends EntityException
{
    public static final String CANNOT_INSTANTIATE_STREAM = "Unable to instantiate stream.  Define a valid constructor.";

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
