package com.onyx.client.exception;

/**
 * Created by tosborn1 on 7/1/16.
 * 
 * This indicates a problem when invoking a remote method.
 * @since 1.2.0
 */
public class MethodInvocationException extends OnyxServerException {

    public static final String NO_SUCH_METHOD = "No Such Method";
    private static final String NO_REGISTERED_OBJECT = "The remote object you are request does not exist!";
    public static final String UNHANDLED_EXCEPTION = "Unhandled exception occurred when making RMI request.";

    /**
     * Default Constructor with message
     * @since 1.2.0
     */
    public MethodInvocationException()
    {
        this.message = MethodInvocationException.NO_REGISTERED_OBJECT;
    }

    /**
     * Default constructor with message and root cause
     * @param message Error message
     * @param cause Root cause
     * @since 1.2.0
     */
    public MethodInvocationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
