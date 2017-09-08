package com.onyx.persistence.query.impl;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.client.base.ConnectionProperties;
import com.onyx.client.push.PushSubscriber;
import com.onyx.client.push.PushPublisher;
import com.onyx.client.push.PushConsumer;
import com.onyx.exception.BufferingException;
import com.onyx.query.QueryListener;
import com.onyx.query.QueryListenerEvent;

import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This is a 3 for one.  It contains the push subscriber information, consumer for the client, and a
 * base query listener.
 *
 */
@SuppressWarnings("unchecked")
public class RemoteQueryListener<T> implements BufferStreamable, QueryListener<T>, PushSubscriber, PushConsumer {

    // Transfer information
    private long listenerId;
    private Object packet;
    private byte subscribeEvent = 1;

    // Publisher information
    private transient ConnectionProperties connectionProperties;
    private transient SocketChannel socketChannel;
    private transient PushPublisher pushPublisher;

    // Responder object
    private transient QueryListener baseListener;

    /**
     * Default constructor
     */
    @SuppressWarnings("unused")
    public RemoteQueryListener()
    {

    }

    /**
     * Constructor with base listener
     * @param baseListener Query listener
     */
    @SuppressWarnings("unused")
    public RemoteQueryListener(QueryListener<T> baseListener)
    {
        this.baseListener = baseListener;
    }

    /**
     * Read from buffer stream
     * @param buffer Buffer Stream to read from
     */
    @Override
    public void read(BufferStream buffer) throws BufferingException {
        listenerId = buffer.getLong();
        packet = buffer.getObject();
        subscribeEvent = buffer.getByte();
    }

    /**
     * Write to buffer stream
     * @param buffer Buffer IO Stream to write to
     */
    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(listenerId);
        buffer.putObject(packet);
        buffer.putByte(subscribeEvent);
    }

    /**
     * Set connection information
     * @param connectionProperties Connection container
     */
    @Override
    public void setConnectionProperties(ConnectionProperties connectionProperties) {
        this.connectionProperties = connectionProperties;
    }

    @Override
    public ConnectionProperties getConnectionProperties() {
        return connectionProperties;
    }

    @Override
    public SocketChannel getChannel() {
        return socketChannel;
    }

    /**
     * Client socket connection
     * @param socketChannel client
     */
    public void setChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    /**
     * Server publisher for push notifications
     * @param pushPublisher Publisher to send messages with
     */
    public void setPushPublisher(PushPublisher pushPublisher) {
        this.pushPublisher = pushPublisher;
    }

    /**
     * Subscriber object id
     * @param pushObjectId Unique subscriber object id
     */
    @Override
    public void setPushObjectId(long pushObjectId) {
        this.listenerId = pushObjectId;
    }

    /**
     * Push subscriber id
     * @return subscriber id
     */
    @Override
    public long getPushObjectId() {
        return listenerId;
    }

    /**
     * Push message
     * @return getter for push message
     */
    @Override
    public Object getPacket() {
        return packet;
    }

    @Override
    public void setPacket(Object object) {
        this.packet = object;
    }

    @Override
    public byte getSubscribeEvent() {
        return this.subscribeEvent;
    }

    @Override
    public void setSubscriberEvent(byte event) {
        this.subscribeEvent = event;
    }

    /**
     * Item has been modified.  This ocurres when an entity met the original criteria
     * when querying the database and was updated.  The updated values still match the critieria
     *
     * @param entity Entity updated via the persistence manager
     *
     * @since 1.3.0
     */
    @Override
    public void onItemUpdated(T entity) {
        QueryEvent event = new QueryEvent(QueryListenerEvent.UPDATE, entity);
        this.pushPublisher.push(this, event);
    }

    /**
     * Item has been inserted.  This ocurres when an entity was saved and it meets the query criteria.
     *
     * @param entity Entity inserted via the persistence manager
     *
     * @since 1.3.0
     */
    @Override
    public void onItemAdded(T entity) {
        QueryEvent event = new QueryEvent(QueryListenerEvent.INSERT, entity);
        this.pushPublisher.push(this, event);
    }

    /**
     * Item has been deleted or no longer meets the critieria of the query.
     *
     * @param entity Entity persisted via the persistence manager
     *
     * @since 1.3.0
     */
    @Override
    public void onItemRemoved(T entity) {
        QueryEvent event = new QueryEvent(QueryListenerEvent.DELETE, entity);
        this.pushPublisher.push(this, event);
    }

    /**
     * Helped to uniquely identify a subscriber
     * @return Hash code of listner and socket channel
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(listenerId);
    }

    /**
     * Comparitor to see if the listener is uniquely identified.  This compares exact identity.
     * @param object Object to compare
     * @return Whether objects are equal
     */
    @Override
    public boolean equals(Object object)
    {
        return (object instanceof RemoteQueryListener
                && ((RemoteQueryListener) object).listenerId == this.listenerId);
    }

    /**
     * Accept query events
     * @param o packet sent from server
     *
     */
    @Override
    public void accept(Object o) {
        QueryEvent event = (QueryEvent) o;
        if(event.getType() == QueryListenerEvent.INSERT)
            baseListener.onItemAdded(event.getEntity());
        else if(event.getType() == QueryListenerEvent.UPDATE)
            baseListener.onItemUpdated(event.getEntity());
        else if(event.getType() == QueryListenerEvent.DELETE)
            baseListener.onItemRemoved(event.getEntity());
    }
}
