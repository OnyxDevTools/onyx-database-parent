package com.onyx.request.pojo;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * Pojo for an entity relationship init body
 */
public class EntityInitializeBody implements ObjectSerializable
{
    private Object entityId;
    private String attribute;
    private String entityType;
    private Object partitionId;

    public Object getEntityId()
    {
        return entityId;
    }

    public void setEntityId(Object entityId)
    {
        this.entityId = entityId;
    }

    public String getAttribute()
    {
        return attribute;
    }

    public void setAttribute(String attribute)
    {
        this.attribute = attribute;
    }

    public String getEntityType()
    {
        return entityType;
    }

    public void setEntityType(String entityType)
    {
        this.entityType = entityType;
    }

    public Object getPartitionId()
    {
        return partitionId;
    }
    public void setPartitionId(Object partitionId)
    {
        this.partitionId = partitionId;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(entityId);
        buffer.writeObject(partitionId);
        buffer.writeObject(attribute);
        buffer.writeObject(entityType);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        entityId = buffer.readObject();
        partitionId = buffer.readObject();
        attribute = (String)buffer.readObject();
        entityType = (String)buffer.readObject();
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
