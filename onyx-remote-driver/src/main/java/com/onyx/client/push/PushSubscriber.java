package com.onyx.client.push;

import com.onyx.client.base.Connection;

import java.nio.channels.SocketChannel;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * Container for publish settings and unique subscription information
 */
public interface PushSubscriber {

    /**
     * Client channel
     * @param channel client communcation socket
     * @since 1.3.0
     */
    void setChannel(SocketChannel channel);

    /**
     * Connection properties.  Container for write buffers and thread pool
     * @param connection Connection container
     * @since 1.3.0
     */
    void setConnection(Connection connection);

    /**
     * Contains connection information used so the subscriber can communicate with the push consumer on the client.
     * @return Connection properties
     *
     * @since 1.3.0
     */
    Connection getConnection();

    /**
     * Socket channel to push subscriber
     * @return Socket channel defined during push registration
     *
     * @since 1.3.0
     */
    SocketChannel getChannel();

    /**
     * Server publisher
     * @param peer Peer responsible for publishing events
     * @since 1.3.0
     */
    void setPushPublisher(PushPublisher peer);

    /**
     * Subscriber unique id per client.  Used to correlate responders.
     * @param pushObjectId Unique subscriber object id
     * @since 1.3.0
     */
    void setPushObjectId(long pushObjectId);

    /**
     * Getter for subscriber id
     * @return subscriber id
     * @since 1.3.0
     */
    long getPushObjectId();

    /**
     * Push message sent back and fourth
     * @return packet being sent to client
     * @since 1.3.0
     */
    Object getPacket();

    /**
     * Set a packet.  This packet is sent to the push consumer.  It is serialized by the server.
     * The only serializable information on the push subscriber should  be the packet and push object id
     * The push object id is used to identify the subscriber and the packet is what is being pushed down to
     * the listener
     *
     * @param object Any serializable object
     *
     * @since 1.3.0
     */
    void setPacket(Object object);

    /**
     * Indicates whether the subscriber should be subscribed or unsubscribed
     * @since 1.3.0
     */
    byte getSubscribeEvent();

    /**
     * Set subscriber event
     * @param event event type 1 for subscribe and 2 for unsubscribe
     * @since 1.3.0
     */
    void setSubscriberEvent(byte event);
}
