package com.onyx.map.serializer;

import com.onyx.persistence.context.SchemaContext;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by timothy.osborn on 4/13/15.
 */
public class SocketBuffer {

    static SchemaContext context;

    static Class[] preRegisterClasses = null;

    /**
     * Constructor without initializing buffer or serializers
     */
    public SocketBuffer(SchemaContext context)
    {
        this.context = context;
    }

    /**
     * Constructor with buffer
     *
     * @param buffer
     */
    public SocketBuffer(ByteBuffer buffer, SchemaContext context)
    {
        this.context = context;
    }

    protected static ThreadLocal<SocketCoder> coderPool = new ThreadLocal();

    /**
     * Initialize
     *
     * @param ctx
     */
    public synchronized static void initialize(SchemaContext ctx, Class... preregister)
    {
        coderPool = new ThreadLocal();
        preRegisterClasses = preregister;
        context = ctx;
    }

    /**
     * Serialize
     *
     * @param value
     * @return
     * @throws IOException
     */
    public static ByteBuffer serialize(final Object value) throws IOException
    {
        SocketCoder coder = coderPool.get();
        if (coder == null)
        {
            coder = new SocketCoder(context, preRegisterClasses);
            coderPool.set(coder);
        }

        byte bytes[] = coder.toByteArray(value);

        final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);

        buffer.rewind();
        return buffer;
    }

    /**
     * Serialize an object and return its buffer
     *
     * @param buffer
     * @return
     */
    public static Object deserialize(final ByteBuffer buffer) throws IOException
    {
        byte[] bytes = new byte[buffer.limit() - buffer.position()];
        buffer.get(bytes);
        return deserialize(bytes);
    }

    /**
     * Serialize an object and return its buffer
     *
     * @param buffer
     * @return
     */
    public static Object deserialize(final byte[] buffer) throws IOException
    {
        SocketCoder coder = coderPool.get();
        if (coder == null)
        {
            coder = new SocketCoder(context, preRegisterClasses);
            coderPool.set(coder);
        }
        return coder.toObject(buffer);
    }

}
