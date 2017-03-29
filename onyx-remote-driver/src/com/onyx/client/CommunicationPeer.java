package com.onyx.client;

import com.onyx.client.auth.AuthenticationManager;
import com.onyx.client.base.ConnectionProperties;
import com.onyx.client.push.PushSubscriber;
import com.onyx.client.push.PushConsumer;
import com.onyx.client.base.RequestToken;
import com.onyx.client.base.engine.PacketTransportEngine;
import com.onyx.client.base.engine.impl.SecurePacketTransportEngine;
import com.onyx.client.base.engine.impl.UnsecuredPacketTransportEngine;
import com.onyx.client.exception.ConnectionFailedException;
import com.onyx.client.exception.OnyxServerException;
import com.onyx.client.exception.RequestTimeoutException;
import com.onyx.client.push.PushRegistrar;
import com.onyx.exception.InitializationException;
import com.onyx.util.map.CompatHashMap;
import com.onyx.util.map.SynchronizedMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Tim Osborn 02/13/2017
 * <p>
 * This class' purpose is to handle all the communication with the server.  It is setup
 * to use either SSL TLS or un secure.
 * <p>
 * It was created in order to improve the performance and remove 3rd party libraries
 *
 * @since 1.2.0
 */
public class CommunicationPeer extends AbstractCommunicationPeer implements OnyxClient, PushRegistrar {

    // Heartbeat and timeout
    private int requestTimeout = 60; // 60 second timeout
    private volatile boolean needsToRunHeartbeat = true; // If there was a response recently, there is no need to send a heartbeat
    private ScheduledExecutorService heartBeatExecutor;

    // Connection information
    private ConnectionProperties connectionProperties;
    private SocketChannel socketChannel;
    private final Map<RequestToken, Consumer> pendingRequests = new ConcurrentHashMap<>();
    private String host;

    // User and authentication
    private AuthenticationManager authenticationManager = null;
    private String user;
    private String password;

    // Keeps track of a unique token.  There can only be about 64 K concurrent requests for a client.
    private volatile short tokenCount = Short.MIN_VALUE+1; // +1 because Short.MIN_VALUE denotes a push event

    /**
     * Default Constructor
     */
    @SuppressWarnings("unused")
    protected CommunicationPeer() {

    }

    /**
     * Handle an response message
     *
     * @param packetType           Indicates if the packet can fit into 1 buffer or multiple
     * @param socketChannel        Socket Channel read from
     * @param connectionProperties ConnectionProperties information containing buffer and thread info
     * @param buffer               ByteBuffer containing message
     * @since 1.2.0
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void handleMessage(byte packetType, SocketChannel socketChannel, ConnectionProperties connectionProperties, ByteBuffer buffer) {
        Consumer consumer;
        RequestToken requestToken = null;
        try {
            requestToken = (RequestToken) serverSerializer.deserialize(buffer, new RequestToken());

            // General unhandled exception that cannot be tied back to a request
            if (requestToken.token == Short.MAX_VALUE) {
                ((Exception) requestToken.packet).printStackTrace();
            } else if(requestToken.token == Short.MIN_VALUE)
            {
                // This indicates a push request
                handlePushMessage(requestToken);
            }

            consumer = pendingRequests.remove(requestToken);

            if (consumer != null) {
                consumer.accept(requestToken.packet);
                needsToRunHeartbeat = false;
            }

        } catch (Exception e) {
            failure(requestToken, e);
        }
    }

    // Map of push consumers
    private Map<Long, PushConsumer> registeredPushConsumers = new SynchronizedMap<>(new CompatHashMap<>());

    /**
     * Respond to a push event.  The consumer will be invoked
     * @param requestToken Request token
     *
     * @since 1.3.0 Support push events
     */
    private void handlePushMessage(RequestToken requestToken)
    {
        final PushSubscriber consumer = (PushSubscriber)requestToken.packet;
        final PushConsumer responder = registeredPushConsumers.get(consumer.getPushObjectId());
        if(responder != null) {
            responder.accept(consumer.getPacket());
        }
    }

    /**
     * Register a push consumer with a subscriber.
     *
     * @param subscriber Object to send to the server to register the push subscription.
     * @param responder Local responder object that will handle the inbound push notifications
     *
     * @throws OnyxServerException Cannot communicate with server
     *
     * @since 1.3.0
     */
    public void register(PushSubscriber subscriber, PushConsumer responder) throws OnyxServerException {
        subscriber.setSubscriberEvent((byte)1);

        long pushId = (long)send(subscriber);
        subscriber.setPushObjectId(pushId);
        registeredPushConsumers.put(pushId, responder);

    }

    /**
     * De register a push subscriber.
     *
     * @param subscriber Subscriber associated to the push listener
     * @throws OnyxServerException Typically indicates cannot connect to server
     * @since 1.3.0
     */
    @Override
    public void unrigister(PushSubscriber subscriber) throws OnyxServerException {
        registeredPushConsumers.remove(subscriber.getPushObjectId());
        subscriber.setSubscriberEvent((byte)2);
        send(subscriber);
    }

    /**
     * Connect to a server with given host and port #
     *
     * @param host Host name or ip address
     * @param port Server port
     * @throws ConnectionFailedException Connection refused or server communication issue
     * @since 1.2.0
     */
    @Override
    public void connect(String host, int port) throws ConnectionFailedException {
        this.port = port;
        this.host = host;
        PacketTransportEngine transportPacketTransportEngine;

        // Setup SSL Settings
        if (useSSL()) {
            SSLContext context;
            try {
                context = SSLContext.getInstance(protocol);
                context.init(createKeyManagers(sslKeystoreFilePath, sslStorePassword, sslKeystorePassword), createTrustManagers(sslTrustStoreFilePath, sslStorePassword), new SecureRandom());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            SSLEngine engine = context.createSSLEngine(host, port);
            engine.setUseClientMode(true);
            transportPacketTransportEngine = new SecurePacketTransportEngine(engine);
        }
        // Not SSL use un-secure transport engine
        else {
            transportPacketTransportEngine = new UnsecuredPacketTransportEngine();
        }

        // Try to open the connection
        try {
            socketChannel = SocketChannel.open();
            socketChannel.socket().setKeepAlive(true);
            socketChannel.socket().setTcpNoDelay(true);
            socketChannel.socket().setReuseAddress(true);
        } catch (IOException e) {
            throw new ConnectionFailedException();
        }

        // Create a buffer and set the transport wrapper
        this.connectionProperties = new ConnectionProperties(transportPacketTransportEngine);
        if (!useSSL()) {
            ((UnsecuredPacketTransportEngine) transportPacketTransportEngine).setSocketChannel(socketChannel);
        }

        try {

            socketChannel.configureBlocking(true);
            int connectTimeout = 5 * 1000;
            socketChannel.socket().connect(new InetSocketAddress(host, port), connectTimeout);
            while (!socketChannel.finishConnect()) {
                LockSupport.parkNanos(100);
            }
        } catch (IOException e) {
            throw new ConnectionFailedException();
        }

        try {
            // Perform Handshake.  If this is unsecured, it is just pass through
            transportPacketTransportEngine.beginHandshake();
            active = doHandshake(socketChannel, connectionProperties);

        } catch (IOException e) {
            throw new ConnectionFailedException();
        }

        connectionProperties.readThread.execute(this::pollForCommunication);
        try {
            this.authenticationManager.verify(this.user, this.password);
            this.resumeHeartBeat();
        } catch (InitializationException e) {

            // Authentication failed, disconnect
            this.close();
        } catch (RequestTimeoutException e) {
            this.close();
            throw new ConnectionFailedException(ConnectionFailedException.CONNECTION_TIMEOUT);
        }
    }

    /**
     * Verify the connection and attempt to re-connect if the connection is not valid
     */
    private void verifyConnection() {
        if(!isConnected())
        {
            try {
                this.connect(this.host, this.port);
            } catch (ConnectionFailedException ignore) {}
        }
    }

    /**
     * Resume Heartbeat.  This is performed after successful authentication
     *
     * @since 1.2.0
     */
    private void resumeHeartBeat() {
        if (this.heartBeatExecutor == null) {
            this.heartBeatExecutor = Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        final Thread t = Executors.defaultThreadFactory().newThread(r);
                        t.setDaemon(true);
                        return t;
                    });

            int HEART_BEAT_INTERVAL = 10 * 1000;
            heartBeatExecutor.scheduleWithFixedDelay(new RetryHeartbeatTask(), HEART_BEAT_INTERVAL, HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Poll for communication responses from the server
     *
     * @since 1.2.0
     */
    private void pollForCommunication() {
        while (active) {
            try {
                read(socketChannel, connectionProperties);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send an object, wrap it with a request, and fire it off to the server.
     * <p>
     * This is non-blocking and will invoke the consumer upon response.
     *
     * @param packet   Object to send to server
     * @param consumer Consumer for the results
     * @throws OnyxServerException Error sending request
     * @since 1.2.0
     */
    @Override
    public void send(Object packet, Consumer<Object> consumer) throws OnyxServerException {

        verifyConnection();
        RequestToken token = new RequestToken(generateNewToken(), (Serializable) packet);
        pendingRequests.put(token, consumer);
        write(socketChannel, connectionProperties, token);
    }
    /**
     * Send a message to the server.  This is blocking and will wait for the response.
     *
     * @param packet Object to send to server
     * @return The response from the server
     * @throws OnyxServerException Error sending request
     * @since 1.2.0
     */
    @Override
    public Object send(Object packet) throws OnyxServerException {
        return send(packet, requestTimeout * 1000);
    }

    /**
     * Send a message to the server.  This is blocking and will wait for the response.
     *
     * @param packet  Object to send to server
     * @param timeout timeout in milliseconds
     * @return The response from the server
     * @since 1.2.0
     */
    private Object send(Object packet, int timeout) {

        verifyConnection();

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicReference<Object> results = new AtomicReference<>();

        // Release the thread lock
        final Consumer consumer = o -> {
            results.set(o);
            countDownLatch.countDown();
        };

        final RequestToken token = new RequestToken(generateNewToken(), (Serializable) packet);
        pendingRequests.put(token, consumer);

        write(socketChannel, connectionProperties, token);

        boolean successResponse;
        try {
            successResponse = countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return new RequestTimeoutException();
        }

        if (!successResponse) {
            pendingRequests.remove(token);
            if(active)
                return new RequestTimeoutException();
        }
        return results.get();
    }

    /**
     * Generates a new token.  Resets it to 0 if we have reached the maximum
     *
     * @return New token id
     */
    private synchronized short generateNewToken() {
        if (tokenCount >= Short.MAX_VALUE - 1) // Short.MAX_VALUE is reserved for un-correlated error
            tokenCount = Short.MIN_VALUE+1;
        return tokenCount++;
    }

    /**
     * Close connection
     *
     * @since 1.2.0
     */
    @Override
    public void close() {
        try {
            active = false;
            connectionProperties.readThread.shutdown();
            closeConnection(socketChannel, connectionProperties);

            needsToRunHeartbeat = false;
            pendingRequests.clear();
            if(this.heartBeatExecutor != null) {
                this.heartBeatExecutor.shutdown();
            }
        } catch (IOException ignore) {
        }
    }

    /**
     * Getter for isConnected
     *
     * @return Whether the socket channel is connected
     * @since 1.2.0
     */
    @Override
    public boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    /**
     * Set the timeout in seconds
     *
     * @param timeout Connection/Request timeout
     * @since 1.2.0
     */
    @Override
    public void setTimeout(int timeout) {
        this.requestTimeout = timeout;
    }

    /**
     * Get the timeout in seconds for a request
     *
     * @return timeout
     * @since 1.2.0
     */
    @Override
    public int getTimeout() {
        return requestTimeout;
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * Set User credentials for persistant authentication
     * @param user Username
     * @param password Password
     *
     * @since 1.2.0
     */
    public void setCredentials(String user, String password) {
        this.user = user;
        this.password = password;
    }

    /**
     * Message failure. Respond to the failure by sending the exception as the response.
     *
     * @param token Message ID
     * @param e Exception that caused it to fail
     *
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    protected void failure(RequestToken token, Exception e) {
        final Consumer consumer = pendingRequests.remove(token);
        if (consumer != null) {
            consumer.accept(e);
        }
        e.printStackTrace();
    }

    private class RetryHeartbeatTask implements Runnable
    {
        /**
         * Run timer task to execute a heartbeat
         */
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            Object result = null;
            try {
                // If there were no recent responses within the last 5 seconds, run a heartbeat
                if(needsToRunHeartbeat) {
                    int heartBeatTimeout = 1000 * 10;
                    result = send(null, heartBeatTimeout);
                } else
                {
                    needsToRunHeartbeat = true;
                }
            } catch (Exception e) {
                result = e;
            }

            // If the connection is still active && there was an error during the heartbeat, try to re-connect
            // After re-connect, retry the pending requests
            if (active
                    && result != null
                    && result instanceof Exception) {
                try {
                    try {
                        socketChannel.close();
                    } catch (IOException ignore) {}
                    connect(host, port);

                    if (active
                            && socketChannel.isConnected()
                            && socketChannel.isOpen()) {

                        // If there are more than 20 requests, fail the requests and flush the queue
                        if(pendingRequests.size() > 5)
                        {
                            pendingRequests.forEach((requestToken, consumer) -> consumer.accept(new InitializationException(InitializationException.CONNECTION_EXCEPTION)));
                            pendingRequests.clear();
                        }
                        // Re-send all failed packets
                        pendingRequests.forEach((requestToken, consumer) ->
                        {
                            // Ignore heartbeat packets
                            if (requestToken.packet != null) {
                                write(socketChannel, connectionProperties, requestToken);
                            }
                        });
                    }
                } catch (ConnectionFailedException ignore) {
                    // If there are more than 20 requests, fail the requests and flush the queue
                    pendingRequests.forEach((requestToken, consumer) -> consumer.accept(new InitializationException(InitializationException.CONNECTION_EXCEPTION)));
                    pendingRequests.clear();
                }
            }
        }
    }
}
