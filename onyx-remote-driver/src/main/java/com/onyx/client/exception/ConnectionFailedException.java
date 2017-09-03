package com.onyx.client.exception;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * Client failed to connect
 */
public class ConnectionFailedException extends OnyxServerException {

    public ConnectionFailedException()
    {

    }

    public ConnectionFailedException(@SuppressWarnings("SameParameterValue") String message)
    {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
