package com.onyx.server.base;

import com.onyx.application.OnyxServer;
import com.onyx.buffer.BufferStream;
import com.onyx.client.AbstractCommunicationPeer;
import com.onyx.client.base.*;
import com.onyx.client.base.engine.PacketTransportEngine;
import com.onyx.client.base.engine.impl.SecurePacketTransportEngine;
import com.onyx.client.base.engine.impl.UnsecuredPacketTransportEngine;
import com.onyx.client.exception.*;
import com.onyx.client.handlers.RequestHandler;
import com.onyx.exception.InitializationException;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

/**
 * Tim Osborn 02/13/2016
 *
 * @since 1.2.0
 * <p>
 * The purpose of this class is to route connections and traffic.  It has been added
 * as a response to remove 3rd party dependencies and improve performance.  Also, to
 * simplify SSL communication.  All socket server communication must go through here.
 * <p>
 * This utilizes off heap buffering.  It sets up a buffer pool for how many active threads you can have.
 * Each connection buffer pool contains 5 allocated buffers with a minimum of 16k bytes.  Be
 * wary on how much you allocate.
 */
public class CommunicationServer extends AbstractCommunicationPeer implements OnyxServer {

    private SSLContext sslContext; // SSL Context if used.  Otherwise this will be null
    private Selector selector; // Selector for inbound communication
    protected RequestHandler requestHandler; // Handler for responding to requests
    private ServerSocketChannel serverSocketChannel = null;

    // Array of buffer pools for connections
    private ConnectionBufferPool[] connectionBufferPools;

    // Round robin for selecting buffer pools for inbound connections
    private volatile int connectionRoundRobin = 0;

    // Thread properties
    private int maxWorkerThreads = 16; // Worker thread number that is the max number of threads calling request handlers
    private CountDownLatch daemonCountDownLatch; // Server Count down latch.  Used to keep server alive within a daemon
    private ExecutorService workerThreadPool; // Worker thread pool for executing request pools
    private final ExecutorService daemonService = Executors.newSingleThreadExecutor(); // Daemon thread pool used to keep alive

    /**
     * Constructor
     *
     * @since 1.2.0
     */
    protected CommunicationServer() {

    }

    /**
     * Retrieve a round robin buffer pool.  This is used to sparse the connections
     * to given allocated buffers and thread pools.  This must be thread safe
     *
     * @return A ConnectionProperties Buffer Pool
     * @since 1.2.0
     */
    private synchronized ConnectionBufferPool getRoundRobinConnectionBuffer() {
        ConnectionBufferPool pool;
        if (connectionRoundRobin >= maxWorkerThreads)
            connectionRoundRobin = 0;

        pool = connectionBufferPools[connectionRoundRobin];
        connectionRoundRobin++;
        return pool;
    }

    /**
     * Start Server
     *
     * @since 1.2.0
     */
    public void start() {

        try {
            workerThreadPool = Executors.newFixedThreadPool(maxWorkerThreads);

            int appBufferSize;
            int packetBufferSize;

            // Define Buffer Pool for SSL and identify packet size
            if (useSSL()) {
                sslContext = SSLContext.getInstance(protocol);
                sslContext.init(createKeyManagers(this.sslKeystoreFilePath, this.sslStorePassword, this.sslKeystorePassword), createTrustManagers(this.sslTrustStoreFilePath, this.sslTrustStorePassword), new SecureRandom());
                SSLEngine sslEngine = sslContext.createSSLEngine();
                SSLSession dummySession = sslEngine.getSession();
                appBufferSize = dummySession.getApplicationBufferSize();
                packetBufferSize = dummySession.getPacketBufferSize();
                dummySession.invalidate();
            } else {
                UnsecuredPacketTransportEngine unsecuredEngine = new UnsecuredPacketTransportEngine();
                appBufferSize = unsecuredEngine.getApplicationSize();
                packetBufferSize = unsecuredEngine.getPacketSize();
            }

            selector = SelectorProvider.provider().openSelector();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().setReuseAddress(true);
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress("0.0.0.0", port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            // Create Buffer Pool for connections
            connectionBufferPools = new ConnectionBufferPool[maxWorkerThreads];
            for (int i = 0; i < maxWorkerThreads; i++)
                connectionBufferPools[i] = new ConnectionBufferPool(appBufferSize, packetBufferSize);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        daemonService.execute(() -> {
            try {
                pollForCommunication();
            } catch (ServerClosedException e) {
                active = false;
            }
        });
        active = true;
    }

    /**
     * Poll the server connections for inbound communication
     *
     * @throws ServerClosedException Whoops, the server closed.  No need to be reading any more data
     * @since 1.2.0
     */
    private void pollForCommunication() throws ServerClosedException {

        while (active) {
            try {
                selector.select();
            } catch (IOException e) {
                throw new ServerClosedException(e);
            }
            final Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();

            // Iterate through all the selection keys that have pending reads
            while (selectedKeys.hasNext()) {
                final SelectionKey key = selectedKeys.next();
                selectedKeys.remove();
                // Ensure connection still open
                if (!key.isValid()) {
                    handleEndOfStream((SocketChannel) key.channel(), (ConnectionProperties)key.attachment());
                    continue;
                }
                try {
                    if (key.isAcceptable()) {
                        try {
                            accept(key);
                        } catch (Exception ignore) {
                            handleEndOfStream((SocketChannel) key.channel(), (ConnectionProperties) key.attachment());
                        }
                    } else if (key.isReadable()) {
                        // Read from the connectionProperties.  Notice it goes down on the readThread for the connectionProperties.
                        // That is a shared thread pool for multiple connections
                        final ConnectionProperties connectionProperties = (ConnectionProperties) key.attachment();
                        if(!connectionProperties.isReading) {
                        connectionProperties.readThread.execute(() -> {
                            if (key.channel().isOpen()) {
                                read((SocketChannel) key.channel(), (ConnectionProperties) key.attachment());
                            }
                        });
                    }
                    }
                } catch (CancelledKeyException ignore) {
                } catch (Exception e) {
                    failure(null, e);
                }
            }
        }

        // Added wait so we dont spin valuable cpu cycles
        try {
            Thread.sleep(20);
        } catch (InterruptedException ignore) {}
    }

    /**
     * Handle an inbound message
     *
     * @param packetType           Indicates if the packet can fit into 1 buffer or multiple
     * @param socketChannel        Socket Channel read from
     * @param connectionProperties ConnectionProperties information containing buffer and thread info
     * @param buffer               ByteBuffer containing message
     * @since 1.2.0
     */
    @Override
    protected void handleMessage(byte packetType, SocketChannel socketChannel, ConnectionProperties connectionProperties, ByteBuffer buffer) {
        RequestToken message = null;

        final boolean isInLargePacket = (packetType != SINGLE_PACKET);
        if (!isInLargePacket) {
            message = parseRequestToken(socketChannel, connectionProperties, buffer);
        }

        final RequestToken threadPoolMessage = message;
        workerThreadPool.execute(() -> {
            final RequestToken useThisRequestToken = (isInLargePacket) ? parseRequestToken(socketChannel, connectionProperties, buffer) : threadPoolMessage;
            if (isInLargePacket) {
                BufferStream.recycle(buffer);
            }
            if (useThisRequestToken.packet != null) {
                try {
                    useThisRequestToken.packet = (Serializable) requestHandler.accept(connectionProperties, useThisRequestToken.packet);
                } catch (Exception e) {
                    useThisRequestToken.packet = new MethodInvocationException(MethodInvocationException.UNHANDLED_EXCEPTION, e);
                }
            }
            write(socketChannel, connectionProperties, useThisRequestToken);
        });
    }

    private RequestToken parseRequestToken(SocketChannel socketChannel, ConnectionProperties connectionProperties, ByteBuffer buffer) {
        RequestToken message = null;
        try {
            message = (RequestToken) serverSerializer.deserialize(buffer, new RequestToken());
        } catch (Exception e) {
            // Error de-serializing packet.  Send a response back to the client
            RequestToken token = new RequestToken(Short.MAX_VALUE, new SerializationException(e));
            write(socketChannel, connectionProperties, token);
            failure(token, e);
        }

        return message;
    }

    /**
     * Accept an inbound connection
     *
     * @param key Selection Key
     * @throws Exception ConnectionProperties was not successful
     * @since 1.2.0
     */
    private void accept(SelectionKey key) throws Exception {

        final SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);

        PacketTransportEngine transportPacketTransportEngine;

        // Determine transport packetTransportEngine
        if (useSSL()) {
            final SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.beginHandshake();
            transportPacketTransportEngine = new SecurePacketTransportEngine(engine);
        } else {
            transportPacketTransportEngine = new UnsecuredPacketTransportEngine(socketChannel);
        }

        final ConnectionBufferPool bufferPool = getRoundRobinConnectionBuffer();
        // Send the buffer pool into the connectionProperties so that they may retain its references
        final ConnectionProperties connectionProperties = new ConnectionProperties(transportPacketTransportEngine, bufferPool);

        // Perform handshake.  If this is secure SSL, this does something otherwise, it is just pass through
        if (doHandshake(socketChannel, connectionProperties)) {
            socketChannel.register(selector, SelectionKey.OP_READ, connectionProperties);
        } else {
            // Poo, no talking to you
            socketChannel.close();
        }

    }


    /**
     * Stop Server
     *
     * @since 1.2.0
     */
    public void stop() {
        active = false;

        if (daemonCountDownLatch != null)
            daemonCountDownLatch.countDown();
        workerThreadPool.shutdown();
        daemonService.shutdown();
        try {
            selector.wakeup();
            serverSocketChannel.socket().close();
            serverSocketChannel.close();
        } catch (IOException ignore) {
        }
    }

    /**
     * Join Server.  Have it pause on a daemon thread
     *
     * @since 1.2.0
     */
    @Override
    public void join() {
        daemonCountDownLatch = new CountDownLatch(1);
        try {
            daemonCountDownLatch.await();
        } catch (InterruptedException ignore) {

        }
    }

    /**
     * Credentials are not set here.  This is not to be used.  If you want it secure
     * setup a keystore and trust store.  If you do not choose to use SSL, auth is done
     * on an application level.
     *
     * @param user     Username
     * @param password Password
     * @since 1.2.0
     */
    @Override
    public void setCredentials(String user, String password) {
    }

    /**
     * Set Max Number of worker threads.  This is the threads running the request handlers
     *
     * @param maxThreads Number of io threads
     * @since 1.2.0
     */
    @Override
    public void setMaxWorkerThreads(int maxThreads) {
        this.maxWorkerThreads = maxThreads;
    }

    /**
     * Identify whether the application is running or not
     *
     * @return boolean value
     * @since 1.2.0
     */
    @Override
    public boolean isRunning() {
        return active;
    }

    /**
     * Get Server port.  This defaults to 8080
     *
     * @return int value for port number
     * @since 1.2.0
     */
    @Override
    public int getPort() {
        return this.port;
    }

    /**
     * Set the port number
     *
     * @param port Port Number
     * @since 1.2.0
     */
    @Override
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Failure within the server.  This should be logged
     *
     * @param token Original request
     * @param e     The underlying exception
     * @since 1.2.0
     */
    protected void failure(RequestToken token, Exception e) {
        if (!(e instanceof InitializationException))
            e.printStackTrace();
    }
}
