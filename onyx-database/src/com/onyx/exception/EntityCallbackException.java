package com.onyx.exception;

/**
 * Created by cosborn on 12/29/2014.
 */
public class EntityCallbackException extends EntityException
{

    protected String callbackMethod;

    public String getCallbackMethod() {
        return callbackMethod;
    }

    public static final String INVOCATION = "Exception occurred when invoking callback: ";

    public EntityCallbackException()
    {

    }

    public EntityCallbackException(String methodName, String message, Throwable cause)
    {

        super(message + methodName, cause);
        callbackMethod = methodName;
    }
}
