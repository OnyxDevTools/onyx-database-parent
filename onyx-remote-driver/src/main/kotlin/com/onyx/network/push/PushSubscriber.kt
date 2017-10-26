package com.onyx.network.push

import com.onyx.network.connection.Connection
import java.nio.channels.SocketChannel

/**
 * Created by tosborn1 on 3/27/17.
 *
 * Container for publish settings and unique subscription information
 */
interface PushSubscriber {

    /**
     * Contains connection information used so the subscriber can communicate with the push consumer on the client.
     *
     * @since 1.3.0
     */
    var connection: Connection?

    /**
     * Socket channel to push subscriber
     *
     * @since 1.3.0
     */
    var channel: SocketChannel?

    /**
     * Getter for subscriber id
     * @since 1.3.0
     */
    var pushObjectId: Long

    /**
     * Set a packet.  This packet is sent to the push consumer.  It is serialized by the server.
     * The only serializable information on the push subscriber should  be the packet and push object id
     * The push object id is used to identify the subscriber and the packet is what is being pushed down to
     * the listener
     *
     * @since 1.3.0
     */
    var packet: Any?

    /**
     * Indicates whether the subscriber should be subscribed or un-subscribed
     * @since 1.3.0
     */
    val subscribeEvent: Byte

    /**
     * Server publisher
     * @param peer Peer responsible for publishing events
     * @since 1.3.0
     */
    fun setPushPublisher(peer: PushPublisher)

    /**
     * Set subscriber event
     * @param event event type 1 for subscribe and 2 for un-subscribe
     * @since 1.3.0
     */
    fun setSubscriberEvent(event: Byte)
}
