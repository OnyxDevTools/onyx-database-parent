package com.onyx.request.pojo;

import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by tosborn on 8/30/14.
 *
 * Pojo for entity request
 */
public class EntityRequestBody implements ObjectSerializable
{
    private Object entity;
    private String type;
    private Object id;
    private String partitionId = "";

    public Object getEntity()
    {
        return entity;
    }

    public void setEntity(Object entity)
    {
        this.entity = entity;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Object getId()
    {
        return id;
    }

    public void setId(Object id)
    {
        this.id = id;
    }

    public String getPartitionId()
    {
        return partitionId;
    }

    public void setPartitionId(String partitionId)
    {
        this.partitionId = partitionId;
    }


    /**
     * Custom serialization to write object to a buffer
     *
     * @param buffer Object Buffer
     * @throws IOException IO Exception when reading or writing to buffer
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(entity);
        buffer.writeObject(type);
        buffer.writeObject(id);
    }

    /**
     * Custom serialization to read an object from a buffer
     *
     * @param buffer Object Buffer
     * @throws IOException IO Exception when reading or writing to buffer
     */
    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        entity = buffer.readObject();
        type = (String)buffer.readObject();
        id = buffer.readObject();
    }

    /**
     * Read object with position
     *
     * @param buffer Object Buffer
     * @param position Position to verify
     * @throws IOException IO Exception when reading or writing to buffer
     */
    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {}
}
