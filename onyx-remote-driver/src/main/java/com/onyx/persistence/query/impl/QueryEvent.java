package com.onyx.persistence.query.impl;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.QueryListenerEvent;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * Pojo for a query event push response
 */
@SuppressWarnings("unchecked WeakerAccess")
public class QueryEvent<T> implements BufferStreamable {

    @SuppressWarnings("unused")
    public QueryEvent()
    {

    }

    /**
     * Constructor with type entity and listener id
     * @param type event type
     * @param entity managed entity involved in query cache change
     *
     * @since 1.3.0
     */
    @SuppressWarnings("WeakerAccess")
    public QueryEvent(QueryListenerEvent type, T entity)
    {
        this.type = type;
        this.entity = entity;
    }

    private QueryListenerEvent type;
    private T entity;

    /**
     * Read object from buffer
     * @param buffer Buffer Stream to read from
     * @throws BufferingException Hmmm, you messed up
     */
    @Override
    public void read(BufferStream buffer) throws BufferingException {
        this.type = QueryListenerEvent.values()[buffer.getByte()];
        this.entity = (T)buffer.getObject();
    }

    /**
     * Write to buffer stream
     * @param buffer Buffer IO Stream to write to
     */
    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putByte((byte)this.type.ordinal());
        buffer.putObject(this.entity);
    }

    @Override
    public void read(BufferStream bufferStream, SchemaContext context) throws BufferingException {
        read(bufferStream);
    }

    @Override
    public void write(BufferStream bufferStream, SchemaContext context) throws BufferingException {
        write(bufferStream);
    }

    /**
     * Getter for query listener event type
     * @return query cache event type
     */
    public QueryListenerEvent getType() {
        return type;
    }

    /**
     * Setter for query listener event type
     * @param type query listener event type
     */
    @SuppressWarnings("unused")
    public void setType(QueryListenerEvent type) {
        this.type = type;
    }

    /**
     * Entity in question
     * @return entity involved in query chche change
     */
    public T getEntity() {
        return entity;
    }

    /**
     * Entity involved in cache change
     * @param entity Managed entity involved in change
     */
    @SuppressWarnings("unused")
    public void setEntity(T entity) {
        this.entity = entity;
    }

}
