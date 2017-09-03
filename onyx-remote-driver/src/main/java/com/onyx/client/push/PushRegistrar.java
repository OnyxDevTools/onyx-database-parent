package com.onyx.client.push;

import com.onyx.client.exception.OnyxServerException;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * Register a consumer with the given subscriber information
 */
public interface PushRegistrar {

    /**
     * Register a consumer with a subscriber
     * @param consumer Consumes the push notifications
     * @param responder Uniquely identifies a subscriber
     * @throws OnyxServerException Communication error
     *
     * @since 1.3.0
     */
    void register(PushSubscriber consumer, PushConsumer responder) throws OnyxServerException;

    /**
     * De register a push registration.  This API is for clients to take the original subscriber containing
     * the push identity and send it off to the server to de-register.
     *
     * Note: There is no receipt for this action
     *
     * @param subscriber Subscriber originally registered
     * @throws OnyxServerException Communication Exception
     *
     * @since 1.3.0
     */
    void unrigister(PushSubscriber subscriber) throws OnyxServerException;

}
