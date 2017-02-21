package com.onyx.exception;

/**
 * Created by cosborn on 12/29/2014.
 *
 */
public class EntityCallbackException extends EntityException
{

    private String callbackMethod;

    @SuppressWarnings("unused")
    public String getCallbackMethod() {
        return callbackMethod;
    }

    public static final String INVOCATION = "Exception occurred when invoking callback: ";

    @SuppressWarnings("unused")
    public EntityCallbackException()
    {

    }

    public EntityCallbackException(String methodName, String message, Throwable cause)
    {

        super(message + methodName, cause);
        callbackMethod = methodName;
    }
}
