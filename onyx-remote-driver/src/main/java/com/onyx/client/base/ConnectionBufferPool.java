package com.onyx.client.base;

import com.onyx.buffer.BufferPool;
import com.onyx.buffer.BufferStream;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This class is a reference to the connection's buffer and threading mechanisms.
 *
 * @since 1.2.0
 */
public class ConnectionBufferPool {

    public ExecutorService readThread;
    public ExecutorService writeThread;
    public ByteBuffer writeApplicationData;
    public ByteBuffer writeNetworkData;
    public ByteBuffer readApplicationData;
    public ByteBuffer readNetworkData;

    /**
     * Default Constructor
     * @since 1.2.0
     */
    ConnectionBufferPool()
    {
        readThread = Executors.newSingleThreadExecutor();
        writeThread = Executors.newSingleThreadExecutor();
    }

    /**
     * Constructor with allocation sizes
     * @param applicationBufferSize Size of application buffer
     * @param packetSize Size of network buffer
     *
     * @since 1.2.0
     */
    public ConnectionBufferPool(int applicationBufferSize, int packetSize)
    {
        writeApplicationData = BufferPool.INSTANCE.allocate(applicationBufferSize);
        writeNetworkData = BufferPool.INSTANCE.allocate(packetSize);
        readApplicationData = BufferPool.INSTANCE.allocate(applicationBufferSize);
        readNetworkData = BufferPool.INSTANCE.allocate(packetSize);
        readThread = Executors.newSingleThreadExecutor();
        writeThread = Executors.newSingleThreadExecutor();
    }

}
