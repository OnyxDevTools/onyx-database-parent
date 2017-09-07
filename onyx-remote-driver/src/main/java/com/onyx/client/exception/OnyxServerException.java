package com.onyx.client.exception;

import com.onyx.exception.OnyxException;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

/**
 * Created by tosborn1 on 6/25/16.
 *
 * Base server exception
 */
public abstract class OnyxServerException extends OnyxException implements Serializable
{

    // Error message
    @SuppressWarnings("unused")
    String message;

    @SuppressWarnings("WeakerAccess")
    protected String stackTrace;
    @SuppressWarnings("WeakerAccess")
    protected Throwable cause;

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
    @SuppressWarnings("WeakerAccess")
    public OnyxServerException(String message, Throwable cause)
    {
        this.message = message;
        this.cause = cause;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        cause.printStackTrace(pw);
        this.stackTrace = sw.toString();
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
