package com.onyx.persistence.query

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.network.connection.Connection
import com.onyx.network.push.PushSubscriber
import com.onyx.network.push.PushPublisher
import com.onyx.network.push.PushConsumer
import com.onyx.exception.BufferingException

import java.nio.channels.SocketChannel
import java.util.Objects

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * This is a 3 for one.  It contains the push subscriber information, consumer for the client, and a
 * base query listener.
 *
 */
class RemoteQueryListener<in T>(private val baseListener: QueryListener<T>? = null) : BufferStreamable, QueryListener<T>, PushSubscriber, PushConsumer {

    // Transfer information
    override var pushObjectId: Long = 0
    override var packet: Any? = null
    override var subscribeEvent: Byte = 1

    // Publisher information
    @Transient
    override var connection: Connection? = null
    @Transient
    override var channel: SocketChannel? = null
    @Transient
    private var pushPublisher: PushPublisher? = null


    /**
     * Read from buffer stream
     * @param buffer Buffer Stream to read from
     */
    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        pushObjectId = buffer.long
        packet = buffer.value
        subscribeEvent = buffer.byte
    }

    /**
     * Write to buffer stream
     * @param buffer Buffer IO Stream to write to
     */
    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putLong(pushObjectId)
        buffer.putObject(packet)
        buffer.putByte(subscribeEvent)
    }

    /**
     * Server publisher for push notifications
     * @param peer Publisher to send messages with
     */
    override fun setPushPublisher(peer: PushPublisher) {
        this.pushPublisher = peer
    }

    override fun setSubscriberEvent(event: Byte) {
        this.subscribeEvent = event
    }

    /**
     * Item has been modified.  This occurs when an entity met the original criteria
     * when querying the database and was updated.  The updated values still match the criteria
     *
     * @param item Entity updated via the persistence manager
     *
     * @since 1.3.0
     */
    override fun onItemUpdated(item: T) {
        val event = QueryEvent(QueryListenerEvent.UPDATE, item)
        this.pushPublisher!!.push(this, event)
    }

    /**
     * Item has been inserted.  This occurs when an entity was saved and it meets the query criteria.
     *
     * @param item Entity inserted via the persistence manager
     *
     * @since 1.3.0
     */
    override fun onItemAdded(item: T) {
        val event = QueryEvent(QueryListenerEvent.INSERT, item)
        this.pushPublisher!!.push(this, event)
    }

    /**
     * Item has been deleted or no longer meets the criteria of the query.
     *
     * @param item Entity persisted via the persistence manager
     *
     * @since 1.3.0
     */
    override fun onItemRemoved(item: T) {
        val event = QueryEvent(QueryListenerEvent.DELETE, item)
        this.pushPublisher!!.push(this, event)
    }

    /**
     * Helped to uniquely identify a subscriber
     * @return Hash code of listener and socket channel
     */
    override fun hashCode(): Int = Objects.hash(pushObjectId)

    /**
     * Comparator to see if the listener is uniquely identified.  This compares exact identity.
     * @param other Object to compare
     * @return Whether objects are equal
     */
    override fun equals(other: Any?): Boolean = other is RemoteQueryListener<*> && other.pushObjectId == this.pushObjectId

    /**
     * Accept query events
     * @param o packet sent from server
     */
    override fun accept(o: Any?) {
        @Suppress("UNCHECKED_CAST")
        val event = o as QueryEvent<T>
        when (event.type){
            QueryListenerEvent.INSERT -> baseListener!!.onItemAdded(event.entity!!)
            QueryListenerEvent.UPDATE -> baseListener!!.onItemUpdated(event.entity!!)
            QueryListenerEvent.DELETE -> baseListener!!.onItemRemoved(event.entity!!)
            else -> { }
        }
    }

}
