package com.onyx.client.rmi;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

import java.io.*;

/**
 * Created by tosborn1 on 7/1/16.
 *
 * This is the main packet to send to the server for remote method invocation.
 * @since 1.2.0
 */
public class RMIRequest implements Serializable, Externalizable, BufferStreamable
{

    private String instance;
    private String method;
    private Object[] params;

    /**
     * Default constructor with instance, method, and parameters
     *
     * @param instance Instance name to be registered
     * @param method Method to invoke
     * @param params Parameters to include in the method invocation
     * @since 1.2.0
     */
    RMIRequest(String instance, String method, Object[] params)
    {
        this.instance = instance;
        this.method = method;
        this.params = params;
    }

    /**
     * Default constructor without classes
     * @since 1.2.0
     */
    public RMIRequest()
    {

    }

    /**
     * Write To Serializer buffer
     *
     * @param out output stream
     * @throws IOException error while writing object
     * @since 1.2.0
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(instance);
        out.writeObject(method);

        // Iterate through all the parameters
        if(params != null && params.length > 0) {
            out.writeByte(params.length);
            for (Object param : params) {
                out.writeObject(param);
            }
        }
    }

    /**
     * Read this object in with serialization input stream
     * @param in Input stream
     * @throws IOException Error reading from input stream
     * @throws ClassNotFoundException Cannot instantiate serialized classes
     * @since 1.2.0
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        instance = in.readUTF();
        method = (String)in.readObject();
        if(in.available() > 0) {
            params = new Object[in.readByte()];
        }

        if(params != null) {
            for (int i = 0; i < params.length; i++) {
                params[i] = in.readObject();
            }
        }
        else
        {
            params = new Object[0];
        }
    }

    public String getInstance() {
        return instance;
    }

    @SuppressWarnings("unused")
    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getMethod() {
        return method;
    }

    @SuppressWarnings("unused")
    public void setMethod(String method) {
        this.method = method;
    }

    public Object[] getParams() {
        return params;
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        instance = buffer.getString();
        method = buffer.getString();
        params = (Object[]) buffer.getObject();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putString(instance);
        buffer.putString(method);
        buffer.putObject(params);
    }
}
