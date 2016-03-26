package com.onyx.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.rmi.RemoteException;

/**
 * Created by timothy.osborn on 11/3/14.
 * <p>
 * Base exception for an entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityException extends RemoteException {

    public static final String UNKNOWN_EXCEPTION = "Unknown exception occurred";

    transient public Throwable rootCause = null;

    /**
     * Constructor with cause
     *
     * @param cause
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
     * @param message
     */
    public EntityException(String message)
    {
        super(message);
    }

    /**
     * Constructor with message and cause
     *
     * @param message
     * @param cause
     */
    public EntityException(String message, Throwable cause)
    {
        super(message, cause);
    }

}
