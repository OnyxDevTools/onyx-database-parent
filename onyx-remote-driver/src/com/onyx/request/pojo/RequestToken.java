package com.onyx.request.pojo;


import com.onyx.client.DefaultDatabaseEndpoint;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.SocketBuffer;
import org.omg.PortableServer.RequestProcessingPolicy;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Created by timothy.osborn on 4/1/15.
 */
public class RequestToken implements Externalizable{

    private static short MESSAGE_TOKEN = Short.MIN_VALUE;
    private static Boolean tokenLock = true;

    protected short messageId;
    protected byte type;
    protected byte endpoint;
    protected byte priority;
    protected Object payload;

    protected transient DefaultDatabaseEndpoint.MessageListener listener = null;

    /**
     * Empty Constructor just used for instantiation from serializer
     */
    public RequestToken()
    {
        this.priority =  (byte)RequestPriority.CRITICAL.ordinal();
    }

    /**
     * Constructor with request token type and credentials
     *
     * @param type - This indicates the RPC action that needs to be called
     *
     * @param payload - Token Payload
     */
    public RequestToken(RequestEndpoint endpoint, RequestTokenType type, Object payload)
    {
        this.messageId = generateToken();
        this.type = (byte)type.ordinal();
        this.endpoint = (byte)endpoint.ordinal();
        this.payload = payload;
        this.priority = (byte)RequestPriority.CRITICAL.ordinal();
    }

    /**
     * Constructor with request token type and credentials
     *
     * @param type - This indicates the RPC action that needs to be called
     *
     * @param payload - Token Payload
     */
    public RequestToken(RequestEndpoint endpoint, RequestTokenType type, Object payload, RequestPriority priority)
    {
        this.messageId = generateToken();
        this.type = (byte)type.ordinal();
        this.endpoint = (byte)endpoint.ordinal();
        this.payload = payload;
        this.priority = (byte)priority.ordinal();
    }

    /**
     * Generates a token id value.  The token ID value is represented by an integer.  The integer starts out as the
     * minimum value of an int and increments until it reaches the max value;
     *
     * After the max value of an integer is reached.  It is then reset.
     *
     * This is unique to the client.  NOT, the server
     * @return
     */
    private static short generateToken()
    {
        synchronized (tokenLock)
        {
            MESSAGE_TOKEN++;
            if(MESSAGE_TOKEN == Short.MAX_VALUE)
            {
                MESSAGE_TOKEN = Short.MIN_VALUE;
            }
            return MESSAGE_TOKEN;
        }
    }

    public short getMessageId()
    {
        return messageId;
    }

    public void setMessageId(short messageId)
    {
        this.messageId = messageId;
    }

    public byte getType()
    {
        return type;
    }

    public byte getEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(byte endpoint)
    {
        this.endpoint = endpoint;
    }

    public void setType(byte type)
    {
        this.type = type;
    }

    public Object getPayload()
    {
        return payload;
    }

    public void setPayload(Object payload)
    {
        this.payload = payload;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {

        out.writeShort(messageId);
        out.writeByte(type);
        out.writeByte(endpoint);
        out.writeObject(payload);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        messageId = in.readShort();
        type = in.readByte();
        endpoint = in.readByte();
        payload = in.readObject();
    }


    /**
     * Get Token
     *
     * @param buffer
     * @return
     * @throws IOException
     */
    public static RequestToken getToken(ByteBuffer buffer) throws IOException
    {

        final RequestToken token = new RequestToken();

        token.setMessageId(buffer.getShort());
        token.setType(buffer.get());
        token.setEndpoint(buffer.get());
        token.setPriority(buffer.get());
        token.setPayload(SocketBuffer.deserialize(buffer));
        return token;
    }

    /**
     * Get Byte Buffer token
     *
     * @param token
     * @return
     * @throws IOException
     */
    public static ByteBuffer getPacket(RequestToken token) throws IOException
    {
        ByteBuffer buffer = SocketBuffer.serialize(token.getPayload());
        buffer.rewind();

        ByteBuffer totalBuffer = ObjectBuffer.allocate(buffer.limit() + (Byte.BYTES * 3) + Short.BYTES);

        totalBuffer.putShort(token.getMessageId());
        totalBuffer.put(token.getType());
        totalBuffer.put(token.getEndpoint());
        totalBuffer.put(token.getPriorityByte());
        totalBuffer.put(buffer);

        totalBuffer.rewind();
        return totalBuffer;
    }

    /**
     * Get Priority byte
     *
     * @return
     */
    public byte getPriorityByte()
    {
        return priority;
    }

    /**
     * Set Priority
     *
     * @param priority
     */
    public void setPriority(byte priority)
    {
        this.priority = priority;
    }

    /**
     * Get Priority
     *
     * @return
     */
    public RequestPriority getPriority()
    {
        return RequestPriority.values()[priority];
    }

    /**
     * Reduce Priority
     */
    public void reducePriority()
    {
        if(priority < RequestPriority.MINIMUM.ordinal())
        {
            priority+=1;
        }
    }

    /**
     * Get listener
     *
     * @return
     */
    public synchronized DefaultDatabaseEndpoint.MessageListener getListener() {
        return listener;
    }

    /**
     * Set Listener
     *
     * @param listener
     */
    public synchronized void setListener(DefaultDatabaseEndpoint.MessageListener listener) {
        this.listener = listener;
    }

    @Override
    public int hashCode()
    {
        return (int)messageId;
    }

    @Override
    public boolean equals(Object val)
    {
        if(val instanceof RequestToken && ((RequestToken) val).messageId == messageId)
            return true;

        return false;
    }

    /**
     * To String
     * @return
     */
    public String toString()
    {
        String val = "Message ID: " + this.messageId +
        " Endpoint: " +  RequestEndpoint.values()[this.endpoint].toString() +
        " Type: " + RequestTokenType.values()[this.type].toString() +
        " Priority: " + getPriority().toString() +
        " Payload: " + ((payload == null) ? "" : payload.toString());
        return val;
    }

}
