package com.onyx.client.push;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * Publish a push event.  This is performed by a server.  This should manage the push to the client.
 */
public interface PushPublisher {

    /**
     * Send an arbitrary packet to a client
     * @param pushSubscriber Push subscriber containing connection information.
     * @param message Packet to send
     *
     * @since 1.3.0
     */
    void push(PushSubscriber pushSubscriber, Object message);

    /**
     * Get actual reference of the push subscriber.
     *
     * @param pushSubscriber Placeholder containing identity.  The identity is based on pushObjectId.
     * @return The reference correlating to the placeholder with matching pushObjectId
     *
     * @since 1.3.0
     */
    PushSubscriber getRegisteredSubscriberIdentity(PushSubscriber pushSubscriber);

    /**
     * De-register a push subscriber
     *
     * @param pushSubscriber Subscriber to de-register
     *
     * @since 1.3.0
     */
    void deRegisterSubscriberIdentity(PushSubscriber pushSubscriber);
}
