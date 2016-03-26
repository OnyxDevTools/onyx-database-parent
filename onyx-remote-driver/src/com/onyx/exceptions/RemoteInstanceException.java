package com.onyx.exceptions;

import com.onyx.exception.EntityException;

/**
 * Created by tosborn1 on 3/11/16.
 */
public class RemoteInstanceException extends EntityException
{
    public static final String CANNOT_FIND_SERVER_INSTANCE = "Cannot find server instance with that name";

    protected String instanceName = null;

    /**
     * Constructor
     *
     * @param instanceName
     */
    public RemoteInstanceException(String instanceName)
    {
        super(CANNOT_FIND_SERVER_INSTANCE);
    }

}
