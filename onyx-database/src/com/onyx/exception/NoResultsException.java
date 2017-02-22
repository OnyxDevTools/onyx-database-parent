package com.onyx.exception;

/**
 * Created by timothy.osborn on 11/30/14.
 *
 * Entity does not return results when expected
 */
public class NoResultsException extends EntityException {

    public NoResultsException()
    {
        super();
    }

    @SuppressWarnings("unused")
    public NoResultsException(Throwable e)
    {
        super(e);
    }
}


