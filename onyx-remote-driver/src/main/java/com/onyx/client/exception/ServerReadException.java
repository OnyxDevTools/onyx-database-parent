package com.onyx.client.exception;

/**
 * Created by tosborn1 on 6/25/16.
 *
 * This exception indicates a problem while attempting to read from a connection
 */
public class ServerReadException extends OnyxServerException
{
    private static final String SERVER_READ_EXCEPTION = "An error occurred while attempting to read from a client connection.";

    /**
     * Default constructor
     *
     * @param throwable Root cause exception
     */
    public ServerReadException(Throwable throwable)
    {
        super(SERVER_READ_EXCEPTION, throwable);
    }

}
