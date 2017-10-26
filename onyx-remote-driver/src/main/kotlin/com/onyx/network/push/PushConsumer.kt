package com.onyx.network.push

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This is a consumer object of a push response
 */
interface PushConsumer {

    /**
     * Accept the push notification
     * @param o packet sent from server
     *
     * @since 1.3.0
     */
    fun accept(o: Any?)
}
