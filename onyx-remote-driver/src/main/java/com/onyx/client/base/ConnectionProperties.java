package com.onyx.client.base;

import com.onyx.buffer.BufferPool;
import com.onyx.buffer.BufferStream;
import com.onyx.client.base.engine.PacketTransportEngine;

import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This contains the connection information including the buffers and the thread pool
 * that each connection is assigned to.
 * @since 1.2.0
 */
public class ConnectionProperties extends ConnectionBufferPool
{
    private boolean authenticated = false;

    // PacketTransportEngine for wrapping and unwrapping connection
    public final PacketTransportEngine packetTransportEngine;
    public final ByteBuffer readOverflowData;
    public volatile boolean isReading = false;

    /**
     * Constructor with packetTransportEngine
     * @param packetTransportEngine PacketTransportEngine used to wrap and unwrap packets
     * @since 1.2.0
     */
    public ConnectionProperties(PacketTransportEngine packetTransportEngine)
    {
        super();
        this.packetTransportEngine = packetTransportEngine;
        this.setWriteApplicationData(BufferPool.INSTANCE.allocate(packetTransportEngine.getApplicationSize()));
        this.setWriteNetworkData(BufferPool.INSTANCE.allocate(packetTransportEngine.getPacketSize()));
        this.setReadApplicationData(BufferPool.INSTANCE.allocate(packetTransportEngine.getApplicationSize()));
        this.setReadNetworkData(BufferPool.INSTANCE.allocate(packetTransportEngine.getPacketSize()));
        this.readOverflowData = BufferPool.INSTANCE.allocate(packetTransportEngine.getPacketSize());
    }

    /**
     * Connection with base connection buffer pool.  The references are copied over
     * but, it leaves the base alone.  That is so it can be re-used with other connections.
     *
     * @param packetTransportEngine Used to wrap and unwrap the packets.  This is specific for the connection.
     * @param base Base information including buffers and thread pools
     * @since 1.2.0
     */
    public ConnectionProperties(PacketTransportEngine packetTransportEngine, ConnectionBufferPool base)
    {
        this.packetTransportEngine = packetTransportEngine;

        this.setWriteApplicationData(base.getWriteApplicationData());
        this.setWriteNetworkData(base.getWriteNetworkData());
        this.setReadApplicationData(base.getReadApplicationData());
        this.setReadNetworkData(base.getReadNetworkData());
        this.readOverflowData = BufferPool.INSTANCE.allocate(packetTransportEngine.getPacketSize());
        this.setReadThread(base.getReadThread());
        this.setWriteThread(base.getWriteThread());
    }

    /**
     * Getter for is Authenticated
     * @return isAuthenticated
     * @since 1.2.0
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Setter for is Authenticated
     * @param authenticated Set authenticated to value
     * @since 1.2.0
     */
    public void setAuthenticated(@SuppressWarnings("SameParameterValue") boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     * Handles the remainder of the the buffer fro a read.  This is so that in the next
     * loop left over from the read, the connection retains the fail over.  If partial
     * packets are left orphaned, that would be bad.
     *
     * @since 1.2.0
     */
    public void handleConnectionRemainder()
    {
        // We have some left over data from the last read.  Lets use that for this next iteration
        if(readOverflowData.position() > 0)
        {
            readOverflowData.flip();
            getReadNetworkData().put(readOverflowData);
            readOverflowData.clear();
        }
    }
}