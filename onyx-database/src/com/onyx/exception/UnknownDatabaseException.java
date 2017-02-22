package com.onyx.exception;

/**
 * Created by tosborn1 on 12/31/15.
 *
 */
public class UnknownDatabaseException extends EntityException
{
    @SuppressWarnings("unused")
    protected String cause = null;

    @SuppressWarnings("unused")
    public UnknownDatabaseException()
    {

    }

    @SuppressWarnings("unused")
    public UnknownDatabaseException(Exception e)
    {
        this.cause = e.getMessage();
    }
}
