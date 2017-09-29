package com.onyx.client.base;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;

import java.io.Serializable;

/**
 * Created by tosborn1 on 2/10/17.
 *
 * This class is a token that is sent back and fourth between the client and server.
 */
public class RequestToken implements BufferStreamable
{
    public boolean reTry;
    public short token;
    public Serializable packet;

    public RequestToken()
    {

    }

    public RequestToken(short token, Serializable packet)
    {
        this.token = token;
        this.packet = packet;
    }

    @Override
    public int hashCode()
    {
        return token;
    }

    @Override
    public boolean equals(Object o)
    {
        return (o != null && o instanceof RequestToken && ((RequestToken) o).token == token);
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        token = buffer.getShort();
        packet = (Serializable)buffer.getObject();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putShort(token);
        buffer.putObject(packet);
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