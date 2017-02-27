package com.onyx.client.exception;

import com.onyx.exception.EntityException;

import java.io.Serializable;

/**
 * Created by tosborn1 on 6/25/16.
 *
 * Base server exception
 */
public abstract class OnyxServerException extends EntityException implements Serializable
{

    // Error message
    @SuppressWarnings("unused")
    String message;

    private Throwable cause;

    /**
     * Default Constructor
     */
    OnyxServerException()
    {

    }

    /**
     * Default Constructor with message
     *
     * @param message Cause of error message
     */
    @SuppressWarnings("unused")
    public OnyxServerException(String message)
    {
        this.message = message;
    }

    /**
     * Default Constructor with message and cause
     * @param message Error message
     * @param cause Root cause exception
     */
    OnyxServerException(String message, Throwable cause)
    {
        this.message = message;
        this.cause = cause;
    }

    /**
     * Get Root cause
     * @return the root cause
     */
    @Override
    public Throwable getCause() {
        return cause;
    }
}
