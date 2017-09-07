package com.onyx.request.pojo;

import com.onyx.exception.OnyxException;

/**
 * Created by timothy.osborn on 4/10/15.
 *
 * Response pojo for exception
 */
public class ExceptionResponse
{
    /**
     * Constructor
     *
     * @param exception Underlying Exception
     * @param type Type of exception
     */
    public ExceptionResponse(OnyxException exception, String type)
    {
        this.exception = exception;
        this.exceptionType = type;
    }

    private Object exception;
    private String exceptionType;

    @SuppressWarnings("unused")
    public String getExceptionType() {
        return exceptionType;
    }

    @SuppressWarnings("unused")
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    @SuppressWarnings("unused")
    public Object getException() {
        return exception;
    }

    @SuppressWarnings("unused")
    public void setException(Object exception) {
        this.exception = exception;
    }
}
