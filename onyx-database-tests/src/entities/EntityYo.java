package entities;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;
import java.util.Date;

/**
 * Created by timothy.osborn on 4/2/15.
 */
public class EntityYo implements ObjectSerializable
{
    public String id;
    public Long longValue;
    public Date dateValue;
    public String longStringValue;
    public String otherStringValue;


    public Integer mutableInteger;
    public Long mutableLong;
    public Boolean mutableBoolean;
    public Float mutableFloat;
    public Double mutableDouble;

    public int immutableInteger;
    public long immutableLong;
    public boolean immutableBoolean;
    public float immutableFloat;
    public double immutableDouble;

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(id);
        buffer.writeObject(longValue);
        buffer.writeObject(dateValue);
        buffer.writeObject(longStringValue);
        buffer.writeObject(otherStringValue);

        buffer.writeObject(mutableInteger);
        buffer.writeObject(mutableLong);
        buffer.writeObject(mutableBoolean);
        buffer.writeObject(mutableFloat);
        buffer.writeObject(mutableDouble);

        buffer.writeInt(immutableInteger);
        buffer.writeLong(immutableLong);
        buffer.writeBoolean(immutableBoolean);
        buffer.writeFloat(immutableFloat);
        buffer.writeDouble(immutableDouble);

    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        id = (String)buffer.readObject();
        longValue = (Long)buffer.readObject();
        dateValue = (Date)buffer.readObject();
        longStringValue = (String)buffer.readObject();
        otherStringValue = (String)buffer.readObject();

        mutableInteger = (Integer)buffer.readObject();
        mutableLong = (Long)buffer.readObject();
        mutableBoolean = (Boolean)buffer.readObject();
        mutableFloat = (Float)buffer.readObject();
        mutableDouble = (Double)buffer.readObject();

        immutableInteger = buffer.readInt();
        immutableLong = buffer.readLong();
        immutableBoolean = buffer.readBoolean();
        immutableFloat = buffer.readFloat();
        immutableDouble = buffer.readDouble();

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
