package com.onyx.persistence.query;

import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;

import java.io.IOException;
import java.util.List;


/**
 * Query sum projection.  Get sum of attribute values that match query criteria
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * @deprecated
 */
public class Sum implements QueryProjection<String>, ObjectSerializable {

    protected String value;

    protected String fieldName;

    /**
     * Constructor sets the value to the sum of all of the parameters
     * @param fieldName Attribute Name
     * @param numbers Attribute values
     */
    public Sum(String fieldName, int...numbers) {
        this.fieldName = fieldName;
        int numValue = 0;
        for(int i = 0 ; i<numbers.length;i++){
            numValue += numbers[i];
        }
        value = Integer.toString(numValue);
    }

    /**
     * @return Sum value
     */
    @Override
    public String getValue() {
        return null;
    }

    /**
     * @return Attribute Name
     */
    @Override
    public String getFieldName() {
        return null;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(fieldName);
        buffer.writeObject(value);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        fieldName = (String)buffer.readObject();
        value = (String)buffer.readObject();
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
