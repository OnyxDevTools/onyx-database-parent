package com.onyx.request.pojo;

import com.onyx.exception.EntityException;

/**
 * Created by timothy.osborn on 4/10/15.
 */
public class ExceptionResponse
{
    /**
     * Constructor
     *
     * @param exception
     * @param type
     */
    public ExceptionResponse(EntityException exception, String type)
    {
        this.exception = exception;
        this.exceptionType = type;
    }

    public Object exception;
    public String exceptionType;
}
