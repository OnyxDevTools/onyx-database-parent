package com.onyx.network.push

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * Publish a push event.  This is performed by a server.  This should manage the push to the client.
 */
interface PushPublisher {

    /**
     * Send an arbitrary packet to a client
     * @param pushSubscriber Push subscriber containing connection information.
     * @param message Packet to send
     *
     * @since 1.3.0
     */
    fun push(pushSubscriber: PushSubscriber, message: Any)

    /**
     * Get actual reference of the push subscriber.
     *
     * @param pushSubscriber Placeholder containing identity.  The identity is based on pushObjectId.
     * @return The reference correlating to the placeholder with matching pushObjectId
     *
     * @since 1.3.0
     */
    fun getRegisteredSubscriberIdentity(pushSubscriber: PushSubscriber): PushSubscriber?

    /**
     * De-register a push subscriber
     *
     * @param pushSubscriber Subscriber to de-register
     *
     * @since 1.3.0
     */
    fun deRegisterSubscriberIdentity(pushSubscriber: PushSubscriber)
}
