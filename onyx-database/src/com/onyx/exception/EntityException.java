package com.onyx.exception;

/**
 * Created by timothy.osborn on 11/3/14.
 * <p>
 * Base exception for an entity
 */
public class EntityException extends Exception {

    public static final String UNKNOWN_EXCEPTION = "Unknown exception occurred";

    transient Throwable rootCause = null;

    /**
     * Constructor with cause
     *
     * @param cause Root cause
     */
    public EntityException(Throwable cause)
    {
        super(cause.getLocalizedMessage());
        this.rootCause = cause;
    }

    /**
     * Constructor
     */
    public EntityException()
    {
        super();
    }

    /**
     * Constructor with error message
     *
     * @param message Exception message
     */
    public EntityException(String message)
    {
        super(message);
    }

    /**
     * Constructor with message and cause
     *
     * @param message Exception message
     * @param cause Root cause
     */
    public EntityException(String message, Throwable cause)
    {
        super(message, cause);
    }

}
