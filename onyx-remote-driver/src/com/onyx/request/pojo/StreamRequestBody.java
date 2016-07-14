package com.onyx.request.pojo;

import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;
import com.onyx.persistence.query.Query;
import com.onyx.stream.QueryStream;

import java.io.IOException;

/**
 * Created by tosborn1 on 7/12/16.
 *
 * This is the transport object for the stream api within the PersistenceManager
 */
public class StreamRequestBody  implements ObjectSerializable {

    protected Query query;
    protected Class streamClass;

    /**
     * Constructor with query and stream class
     * @param query
     * @param streamClass
     */
    public StreamRequestBody(Query query, Class streamClass)
    {
        this.query = query;
        this.streamClass = streamClass;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    public Class getStreamClass() {
        return streamClass;
    }

    public void setStreamClass(Class streamClass) {
        this.streamClass = streamClass;
    }

    /**
     * Write Object to buffer
     * @param buffer buffer to write to.
     * @throws IOException
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeObject(query);
        buffer.writeObject(streamClass.getCanonicalName());
    }

    /**
     * Read Object from a buffer
     * @param buffer buffer to read
     * @throws IOException
     */
    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        query = (Query) buffer.readObject();
        try
        {
            streamClass = (Class)Class.forName((String) buffer.readObject());
        }
        catch (ClassNotFoundException e)
        {
            streamClass = null;
        }
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException {

    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }
}
