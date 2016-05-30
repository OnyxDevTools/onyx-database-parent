package com.onyx.exception;

/**
 * Exception thrown when the persistence manager is unable to instantiate the aggregator
 */
public class AggregatorException extends EntityException
{
    public static final String CANNOT_INSTANTIATE_AGGREGATOR = "Unable to instantiate aggregator.  Define a valid constructor.";

    /**
     * Default Constructor
     */
    public AggregatorException()
    {

    }

    /**
     * Default constructor with message
     *
     * @param message Message associated with exception
     */
    public AggregatorException(String message)
    {
        super(message);
    }
}
