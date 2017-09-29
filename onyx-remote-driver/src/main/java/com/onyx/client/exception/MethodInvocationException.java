package com.onyx.client.exception;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;

/**
 * Created by tosborn1 on 7/1/16.
 * 
 * This indicates a problem when invoking a remote method.
 * @since 1.2.0
 */
public class MethodInvocationException extends OnyxServerException implements BufferStreamable {

    public static final String NO_SUCH_METHOD = "No Such Method";
    private static final String NO_REGISTERED_OBJECT = "The remote object you are request does not exist!";
    public static final String UNHANDLED_EXCEPTION = "Unhandled exception occurred when making RMI request.";

    /**
     * Default Constructor with message
     * @since 1.2.0
     */
    public MethodInvocationException()
    {
        this.message = MethodInvocationException.NO_REGISTERED_OBJECT;
    }

    /**
     * Default constructor with message and root cause
     * @param message Error message
     * @param cause Root cause
     * @since 1.2.0
     */
    public MethodInvocationException(String message, Throwable cause)
    {
        super(message, cause);

    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        this.cause = (Throwable) buffer.getObject();
        this.message = buffer.getString();
        this.stackTrace = buffer.getString();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putObject(getCause());
        buffer.putString(message);
        buffer.putString(this.stackTrace);
    }

    @Override
    public void read(BufferStream bufferStream, SchemaContext context) throws BufferingException {
        read(bufferStream);
    }

    @Override
    public void write(BufferStream bufferStream, SchemaContext context) throws BufferingException {
        write(bufferStream);
    }
}
