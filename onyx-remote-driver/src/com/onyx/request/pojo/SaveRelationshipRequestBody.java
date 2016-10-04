package com.onyx.request.pojo;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;
import java.util.Set;

/**
 * Created by timothy.osborn on 4/8/15.
 */
public class SaveRelationshipRequestBody implements ObjectSerializable
{
    protected String type;
    protected Object entity;
    protected String relationship;
    protected Set<Object> identifiers;

    public Object getEntity()
    {
        return entity;
    }

    public void setEntity(Object entity)
    {
        this.entity = entity;
    }

    public String getRelationship()
    {
        return relationship;
    }

    public void setRelationship(String relationship)
    {
        this.relationship = relationship;
    }

    public Set<Object> getIdentifiers()
    {
        return identifiers;
    }

    public void setIdentifiers(Set<Object> identifiers)
    {
        this.identifiers = identifiers;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * Custom serialization to write object to a buffer
     *
     * @param buffer
     * @throws java.io.IOException
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(type);
        buffer.writeObject(entity);
        buffer.writeObject(relationship);
        buffer.writeObject(identifiers);
    }

    /**
     * Custom serialization to read an object from a buffer
     *
     * @param buffer
     * @throws java.io.IOException
     */
    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        type = (String)buffer.readObject();
        entity = buffer.readObject();
        relationship = (String)buffer.readObject();
        identifiers = (Set<Object>)buffer.readObject();
    }

    /**
     * Read object with position
     *
     * @param buffer
     * @param position
     * @throws java.io.IOException
     */
    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }
}
