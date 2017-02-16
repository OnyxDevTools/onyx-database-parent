package com.onyx.exception;


/**
 * Created by timothy.osborn on 4/25/15.
 */
public class QueryException extends EntityException
{
    public static final String CONNECTION_EXCEPTION = "Cannot connect to database endpoint";
    public static final String SERIALIZATION_EXCEPTION = "Query serialization exception has occurred";
    public static final String QUERY_TIMEOUT = "Query timeout has occurred";
    public static final String MISSING_QUERY_ENTITY_TYPE = "Query entity type is not defined";

    private String message = UNKNOWN_EXCEPTION;

    public QueryException(String message)
    {
        this.message = message;
    }

    /**
     * Query Exception with cause
     *
     * @param cause
     */
    public QueryException(Throwable cause)
    {
        super(cause.getMessage());
    }

    /**
     * Query Exception
     *
     * @param message
     * @param cause
     */
    public QueryException(String message, Throwable cause)
    {
        super(message, cause);
        this.message = message;
    }
}
