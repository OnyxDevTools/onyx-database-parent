package com.onyx.transaction;

import com.onyx.persistence.query.Query;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Delete query transaction
 */
public class DeleteQueryTransaction implements ObjectSerializable, Transaction {

    @SuppressWarnings("WeakerAccess")
    public Query query;

    @SuppressWarnings("unused")
    public DeleteQueryTransaction()
    {

    }

    public DeleteQueryTransaction(Query query)
    {
        this.query = query;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeObject(query);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        query = (Query) buffer.readObject();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException {
        this.readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {
        this.readObject(buffer);
    }
}
