package com.onyx.client.exception;

/**
 * Created by tosborn1 on 6/25/16.
 *
 * This exception indicates the failure to write to a client in response
 */
public class ServerWriteException extends OnyxServerException
{
    private static final String SERVER_WRITE_EXCEPTION = "An error occurred while attempting to write to a client connection.";

    /**
     * Empty Constructor
     */
    @SuppressWarnings("unused")
    public ServerWriteException()
    {

    }

    /**
     * Default constructor
     *
     * @param throwable Root cause exception
     */
    @SuppressWarnings("unused")

    public ServerWriteException(Throwable throwable)
    {
        super(SERVER_WRITE_EXCEPTION, throwable);
    }
}
