package com.onyx.request.pojo;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by timothy.osborn on 4/8/15.
 *
 * Pojo for list request
 */
public class EntityListRequestBody implements ObjectSerializable {

    private String entities;
    private String type;

    public String getEntities()
    {
        return entities;
    }

    public void setEntities(String entities)
    {
        this.entities = entities;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }


    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(entities);
        buffer.writeObject(type);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        entities = (String)buffer.readObject();
        type = (String)buffer.readObject();

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
