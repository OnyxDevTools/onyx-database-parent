package com.onyx.client.rmi;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

/**
 * Created by tosborn1 on 7/1/16.
 *
 * This is the main packet to send to the server for remote method invocation.
 * @since 1.2.0
 */
public class RMIRequest implements BufferStreamable
{

    private String instance;
    private byte method;
    private Object[] params;

    /**
     * Default constructor with instance, method, and parameters
     *
     * @param instance Instance name to be registered
     * @param method Method to invoke
     * @param params Parameters to include in the method invocation
     * @since 1.2.0
     */
    RMIRequest(String instance, byte method, Object[] params)
    {
        this.instance = instance;
        this.method = method;
        this.params = params;
    }

    /**
     * Default constructor without classes
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    public RMIRequest()
    {

    }

    public String getInstance() {
        return instance;
    }

    @SuppressWarnings("unused")
    public void setInstance(String instance) {
        this.instance = instance;
    }

    public byte getMethod() {
        return method;
    }

    @SuppressWarnings("unused")
    public void setMethod(byte method) {
        this.method = method;
    }

    public Object[] getParams() {
        return params;
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        instance = buffer.getString();
        method = buffer.getByte();
        params = (Object[]) buffer.getObject();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putString(instance);
        buffer.putByte(method);
        buffer.putObject(params);
    }
}
