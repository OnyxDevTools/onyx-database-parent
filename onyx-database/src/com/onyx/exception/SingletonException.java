package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/12/14.
 */
public class SingletonException extends EntityException
{
    /**
     * Constructor with message
     *
     * @param message
     */
    public SingletonException(String message)
    {
        super(message);
    }

    public SingletonException()
    {
        super();
    }
}
