package com.onyx.client.exception;

/**
 * Created by tosborn1 on 6/25/16.
 *
 * This exception indicates the server has closed
 */
public class ServerClosedException extends OnyxServerException
{
    private static final String SERVER_CLOSED_MESSAGE = "Client connection closed prematurely.";

    /**
     * Default Constructor
     * @param cause The root cause as to why the connection has been closed
     */
    public ServerClosedException(Throwable cause)
    {
        super(SERVER_CLOSED_MESSAGE, cause);
    }

}
