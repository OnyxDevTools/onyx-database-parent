package com.onyx.request.pojo;

import com.onyx.persistence.query.Query;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by timothy.osborn on 3/5/15.
 */
public class EntityQueryBody implements ObjectSerializable, Serializable
{
    public EntityQueryBody()
    {

    }

    protected Query query;

    public Query getQuery()
    {
        return query;
    }

    public void setQuery(Query query)
    {
        this.query = query;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(query);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        query = (Query)buffer.readObject();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }
}
