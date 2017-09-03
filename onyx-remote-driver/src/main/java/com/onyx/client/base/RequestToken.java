package com.onyx.client.base;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

import java.io.Serializable;

/**
 * Created by tosborn1 on 2/10/17.
 *
 * This class is a token that is sent back and fourth between the client and server.
 */
public class RequestToken implements Serializable, BufferStreamable
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

}