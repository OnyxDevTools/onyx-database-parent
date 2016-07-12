package com.onyx.map.serializer;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.map.node.BitMapNode;
import com.onyx.map.node.Record;
import com.onyx.map.node.RecordReference;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by timothy.osborn on 3/22/15.
 */
public class ObjectBuffer
{

    public static final Charset CHARSET = Charset.forName("UTF-8"); // Supported Character set

    protected static final int BUFFER_ALLOCATION = 88; // Initial Buffer allocation size

    public Serializers serializers;

    protected ByteBuffer buffer = null;

    /**
     * Constructor with serializers
     *
     * @param serializers
     */
    public ObjectBuffer(Serializers serializers)
    {
        this.serializers = serializers;
        this.buffer = allocate(BUFFER_ALLOCATION);
    }

    /**
     * Consturctor with initial byte buffer and serializers
     *
     * @param buffer
     * @param serializers
     */
    public ObjectBuffer(ByteBuffer buffer, Serializers serializers)
    {
        this.serializers = serializers;
        this.buffer = buffer;
    }

    public int getSize()
    {
        return buffer.position();
    }

    /**
     * Truncate the buffer and return it
     *
     * @return
     */
    public ByteBuffer getByteBuffer()
    {
        if(buffer.position() != buffer.capacity())
        {
            final ByteBuffer retVal = allocate(buffer.position());
            retVal.put(buffer.array(), 0, buffer.position());
            retVal.rewind();
            return retVal;
        }
        else
        {
            buffer.rewind();
            return buffer;
        }
    }

    /**
     * Read
     *
     * @param type
     * @return
     * @throws java.io.IOException
     */
    public Object read(ObjectType type) throws IOException
    {
        return unwrap(buffer, type.getType(), serializers);
    }

    /**
     * Read Int
     *
     * @return
     */
    public int readInt()
    {
        return buffer.getInt();
    }

    /**
     * Read Date
     *
     * @return
     */
    public Date readDate()
    {
        return new Date(buffer.getLong());
    }

    /**
     * Read Long
     *
     * @return
     */
    public long readLong()
    {
        return buffer.getLong();
    }

    /**
     * Read Long
     *
     * @return
     */
    public double readDouble()
    {
        return buffer.getDouble();
    }

    /**
     * Read Long
     *
     * @return
     */
    public float readFloat()
    {
        return buffer.getFloat();
    }

    /**
     * Read Long
     *
     * @return
     */
    public byte[] readBytes()
    {
        int size = buffer.getInt();
        final byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * Write Boolean
     *
     * @throws java.io.IOException
     */
    public boolean readBoolean() throws IOException
    {
        byte b = buffer.get();
        return (b == 1) ? true : false;
    }

    /**
     * Read Long Array
     * @param size
     * @return
     */
    public long[] readLongArray(int size)
    {
        final long[] array = new long[size];
        for(int i = 0; i < size; i++)
        {
            array[i] = readLong();
        }
        return array;
    }

    /**
     * Read Short
     *
     * @return
     */
    public short readShort()
    {
        return buffer.getShort();
    }


    /**
     * Helper for writing anything to a buffer which is dynamically growing
     *
     * @param object
     * @throws java.io.IOException
     */
    public int writeObject(Object object) throws IOException
    {
        final ByteBuffer newBuffer = wrap(object, serializers);

        ByteBuffer tempBuffer = null;
        if(buffer.capacity() < (newBuffer.limit() + buffer.position()))
        {
            tempBuffer = allocate(buffer.limit() + newBuffer.limit() + BUFFER_ALLOCATION);
            tempBuffer.put(buffer.array(), 0, buffer.position());
            tempBuffer.put(newBuffer);
            buffer = tempBuffer;
        }
        else
        {
            buffer.put(newBuffer);
        }
        return newBuffer.position();
    }

    public void write(ObjectBuffer addBuffer)
    {
        final ByteBuffer bufferToAdd = addBuffer.getByteBuffer();

        ByteBuffer tempBuffer = null;
        if(buffer.capacity() < (bufferToAdd.limit() + buffer.position()))
        {
            tempBuffer = allocate(buffer.limit() + bufferToAdd.limit() + BUFFER_ALLOCATION);
            tempBuffer.put(buffer.array(), 0, buffer.position());
            tempBuffer.put(bufferToAdd);
            buffer = tempBuffer;
        }
        else
        {
            buffer.put(bufferToAdd);
        }
    }

    /**
     * Read Object
     *
     * @return
     * @throws java.io.IOException
     */
    public Object readObject() throws IOException
    {
        return unwrap(buffer, serializers);
    }

    /**
     * Write Short
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeShort(short val) throws IOException
    {
        checkSize(Short.BYTES);
        buffer.putShort(val);
    }

    /**
     * Write Int
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeInt(int val) throws IOException
    {
        checkSize(Integer.BYTES);
        buffer.putInt(val);
    }

    /**
     * Write Byte
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeByte(byte val) throws IOException
    {
        checkSize(Byte.BYTES);
        buffer.put(val);
    }

    /**
     * Write Byte
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeBytes(byte[] val) throws IOException
    {
        checkSize((Byte.BYTES * val.length) + Integer.BYTES);
        buffer.putInt(val.length);
        buffer.put(val);
    }

    /**
     * Write Long
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeLong(long val) throws IOException
    {
        checkSize(Long.BYTES);
        buffer.putLong(val);
    }

    /**
     * Write Boolean
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeBoolean(boolean val) throws IOException
    {
        checkSize(Byte.BYTES);
        buffer.put((val == true) ? (byte) 1 : (byte) 2);
    }

    /**
     * Write Date
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeDate(Date val) throws IOException
    {
        checkSize(Long.BYTES);
        buffer.putLong(val.getTime());
    }

    /**
     * Write Date
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeFloat(Float val) throws IOException
    {
        checkSize(Float.BYTES);
        buffer.putFloat(val);
    }

    /**
     * Write Date
     *
     * @param val
     * @throws java.io.IOException
     */
    public void writeDouble(Double val) throws IOException
    {
        checkSize(Double.BYTES);
        buffer.putDouble(val);
    }

    /**
     * Write Long Array
     *
     * @param values
     * @throws java.io.IOException
     */
    public void writeLongArray(long[] values) throws IOException
    {
        checkSize(Long.BYTES * values.length);
        for(long val : values)
            buffer.putLong(val);
    }

    /**
     * Check size and ensure the buffer has enough space to accommodate
     *
     * @param needs
     */
    protected void checkSize(int needs)
    {
        if(buffer.capacity() < (needs + buffer.position()))
        {
            ByteBuffer tempBuffer = allocate(buffer.limit() + needs + BUFFER_ALLOCATION);
            tempBuffer.put(buffer.array(), 0, buffer.position());
            buffer = tempBuffer;
        }
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     * @param count
     * @return
     */
    public static ByteBuffer allocate(int count)
    {
        final ByteBuffer buffer = ByteBuffer.allocate((int)count);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer;
    }

    /**
     * Unwrap byte buffer into an object
     *
     * @param buffer
     * @return
     * @throws java.io.IOException
     */
    public static Object unwrap(ByteBuffer buffer, byte type, Serializers serializers) throws IOException
    {
        if(type == ObjectType.NULL.getType())
            return null;
        else if(type == ObjectType.LONG.getType())
            return buffer.getLong();
        else if(type == ObjectType.INT.getType())
            return buffer.getInt();
        else if(type == ObjectType.DOUBLE.getType())
            return buffer.getDouble();
        else if(type == ObjectType.FLOAT.getType())
            return buffer.getFloat();
        else if(type == ObjectType.SHORT.getType())
            return buffer.getShort();
        else if(type == ObjectType.BOOLEAN.getType())
            return unwrapBoolean(buffer);
        else if(type == ObjectType.DATE.getType())
            return unwrapDate(buffer);
        else if(type == ObjectType.ENUM.getType())
            return unwrapEnum(buffer);
        else if(type == ObjectType.STRING.getType())
            return unwrapString(buffer);
        else if(type == ObjectType.BUFFER_OBJ.getType())
            return unwrapNamed(buffer, serializers);
        else if(type == ObjectType.BYTES.getType())
            return unwrapBytes(buffer);
        else if(type == ObjectType.HASH_SET.getType())
            return unwrapCollection(buffer, ObjectType.HASH_SET, serializers);
        else if(type == ObjectType.COLLECTION.getType())
            return unwrapCollection(buffer, ObjectType.COLLECTION, serializers);
        else if(type == ObjectType.MAP.getType())
            return unwrapMap(buffer, serializers);
        else if(type == ObjectType.OTHER.getType())
            return unwrapOther(buffer);
        else if(type == ObjectType.ARRAY.getType())
            return unwrapArray(buffer, serializers);
        else if(type == ObjectType.NODE.getType())
            return unwrapNamed(buffer, BitMapNode.class);
        else if(type == ObjectType.RECORD_REFERENCE.getType())
            return unwrapNamed(buffer, RecordReference.class);
        else if(type == ObjectType.RECORD.getType())
            return unwrapNamed(buffer, Record.class);
        else if(type == ObjectType.CHAR.getType())
            return buffer.getChar();
        else if(type == ObjectType.BYTE.getType())
            return buffer.get();
        return null;
    }

    /**
     * Unwrap byte buffer into an object
     *
     * @param buffer
     * @return
     * @throws java.io.IOException
     */
    public static Object unwrap(ByteBuffer buffer, byte type, Serializers serializers, int serializerId) throws IOException
    {
        if(type == ObjectType.NULL.getType())
            return null;

        else if(type == ObjectType.BUFFER_OBJ.getType())
            return unwrapNamed(buffer, serializers, serializerId);
        return null;
    }

    /**
     * Wrap
     *
     * @param value
     * @return
     * @throws java.io.IOException
     */
    public static ByteBuffer wrap(Object value, Serializers serializers) throws IOException
    {
        if(value == null)
            return wrapNull();
        else if(value instanceof BitMapNode)
            return wrapNamed((ObjectSerializable)value, ObjectType.NODE);
        else if(value instanceof RecordReference)
            return wrapNamed((ObjectSerializable)value, ObjectType.RECORD_REFERENCE);
        else if(value instanceof Record)
            return wrapNamed((ObjectSerializable)value, ObjectType.RECORD);
        else if(value instanceof ObjectSerializable)
            return wrapNamed(value, serializers);
        else if(value instanceof Long || value.getClass() == long.class)
            return wrapLong((Long)value);
        else if(value instanceof Short || value.getClass() == short.class)
            return wrapShort((Short)value);
        else if(value instanceof Date)
            return wrapDate((Date) value);
        else if(value.getClass().isEnum())
            return wrapEnum((Enum)value);
        else if(value instanceof Integer || value.getClass() == int.class)
            return wrapInt((Integer) value);
        else if(value instanceof Character || value.getClass() == char.class)
            return wrapChar((char)value);
        else if(value instanceof Byte || value.getClass() == byte.class)
            return wrapByte((byte)value);
        else if(value instanceof Integer || value.getClass() == int.class)
            return wrapInt((Integer) value);
        else if(value instanceof Double || value.getClass() == double.class)
            return wrapDouble((Double) value);
        else if(value instanceof Float || value.getClass() == float.class)
            return wrapFloat((Float) value);
        else if(value instanceof Boolean || value.getClass() == boolean.class)
            return wrapBoolean((Boolean) value);
        else if(value instanceof byte[])
            return wrapBytes((byte[]) value);
        else if(value instanceof String)
            return wrapString((String) value);
        else if(value.getClass().isArray())
            return wrapArray((Object[])value, serializers);
        else if(value instanceof Collection)
            return wrapCollection((Collection) value, serializers);
        else if(value instanceof Map)
            return wrapMap((Map)value, serializers);
        else
            return wrapOther(value);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Wrap Methods
    //
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Wrap Null
     *
     * @return
     */
    public static ByteBuffer wrapNull()
    {
        final ByteBuffer buffer = allocate(Byte.BYTES); // Just type indicator
        buffer.put(ObjectType.NULL.getType());
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Long
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapLong(long value)
    {
        final ByteBuffer buffer = allocate(Long.BYTES + Byte.BYTES); // Long size + type indicator
        buffer.put(ObjectType.LONG.getType());
        buffer.putLong((Long) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Class
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapClass(Class value)
    {
        short classNameLength = (short)value.getCanonicalName().length();
        final ByteBuffer buffer = allocate(Short.BYTES + Byte.BYTES + classNameLength); // Long size + type indicator
        buffer.put(ObjectType.CLASS.getType());
        buffer.putShort(classNameLength);
        buffer.put(value.getCanonicalName().getBytes(CHARSET));
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Long
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapDate(Date value)
    {
        final ByteBuffer buffer = allocate(Long.BYTES + Byte.BYTES); // Long size + type indicator
        buffer.put(ObjectType.DATE.getType());
        buffer.putLong(((Date) value).getTime());
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Short
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapShort(short value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + Short.BYTES); // Short size + type indicator
        buffer.put(ObjectType.SHORT.getType());
        buffer.putShort((Short) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Int
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapInt(int value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + Integer.BYTES); // Integer size + type indicator
        buffer.put(ObjectType.INT.getType());
        buffer.putInt((Integer) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Int
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapChar(char value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + 2); // Integer size + type indicator
        buffer.put(ObjectType.CHAR.getType());
        buffer.putChar((char) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Int
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapByte(byte value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + Byte.BYTES); // Integer size + type indicator
        buffer.put(ObjectType.BYTE.getType());
        buffer.put((byte) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Double
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapDouble(double value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + Double.BYTES); // Double size + type indicator
        buffer.put(ObjectType.DOUBLE.getType());
        buffer.putDouble((Double) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Boolean
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapBoolean(boolean value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + 1); // byte size + type indicator
        buffer.put(ObjectType.BOOLEAN.getType());
        buffer.put(((Boolean)value == true) ? (byte)1 : (byte)2);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Float
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapFloat(float value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + Float.BYTES); // Float size + type indicator
        buffer.put(ObjectType.FLOAT.getType());
        buffer.putFloat((Float) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Byte Array
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapBytes(byte[] value)
    {
        final ByteBuffer buffer = allocate(Byte.BYTES + value.length + Integer.BYTES); // byte[] length size + type indicator
        buffer.put(ObjectType.BYTES.getType());
        buffer.putInt(value.length);
        buffer.put((byte[]) value);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap String
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapString(String value)
    {
        final int length = value.length();
        final byte[] stringBytes = value.getBytes(CHARSET);

        final ByteBuffer buffer = allocate(Byte.BYTES + stringBytes.length + Integer.BYTES); // type indicator + string bytes + string length
        buffer.put(ObjectType.STRING.getType());
        buffer.putInt(length);
        buffer.put(stringBytes);
        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap Enum
     *
     * @param enumValue
     * @return
     */
    public static ByteBuffer wrapEnum(Enum<?> enumValue)
    {
        final byte[] stringBytes = enumValue.getDeclaringClass().getCanonicalName().getBytes(CHARSET);
        final byte[] nameBytes = enumValue.name().getBytes(CHARSET);

        final ByteBuffer buffer = allocate(Byte.BYTES + stringBytes.length + nameBytes.length + Short.BYTES + Short.BYTES); // Float size + type indicator
        buffer.put(ObjectType.ENUM.getType());
        buffer.putShort((short) stringBytes.length);
        buffer.put(stringBytes);
        buffer.putShort((short) nameBytes.length);
        buffer.put(nameBytes);

        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap a named object that implements ByteBufferSerializable
     *
     * @param value
     * @param serializers
     * @return
     * @throws java.io.IOException
     */
    public static ByteBuffer wrapNamed(Object value, Serializers serializers) throws IOException
    {
        ObjectBuffer objectBuffer = new ObjectBuffer(serializers);
        objectBuffer.writeByte(ObjectType.BUFFER_OBJ.getType());


        Short customType = serializers.getSerializerId(value.getClass().getCanonicalName());

        // Create a new custom serializer
        if(customType == null)
        {
            customType = serializers.add(value.getClass().getCanonicalName());
        }

        objectBuffer.writeShort(customType); // Put the class type

        ((ObjectSerializable)value).writeObject(objectBuffer);

        return objectBuffer.getByteBuffer();
    }

    /**
     * Wrap a named object that implements ByteBufferSerializable
     *
     * @param value
     * @param type
     * @return
     * @throws java.io.IOException
     */
    public static ByteBuffer wrapNamed(Object value, ObjectType type) throws IOException
    {
        ObjectBuffer objectBuffer = new ObjectBuffer(null, null);
        objectBuffer.writeByte(type.getType());
        ((ObjectSerializable)value).writeObject(objectBuffer);

        return objectBuffer.getByteBuffer();
    }

    /**
     * Wrap a collection of items
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapCollection(Collection value, Serializers serializers) throws IOException
    {
        final Collection list = (Collection) value;
        final Iterator iterator = list.iterator();

        final ByteBuffer[] buffers = new ByteBuffer[list.size()];

        int i = 0;
        Object obj = null;
        int totalSize = 0;

        while(iterator.hasNext())
        {
            obj = iterator.next();
            buffers[i] = wrap(obj, serializers);
            buffers[i].rewind();
            totalSize += buffers[i].limit();
            i++;
        }

        final ByteBuffer buffer = allocate(totalSize + Byte.BYTES + Integer.BYTES);

        if(value instanceof HashSet)
        {
            buffer.put(ObjectType.HASH_SET.getType());
        }
        else
        {
            buffer.put(ObjectType.COLLECTION.getType());
        }

        buffer.putInt(i); // Put the size of the array

        for(ByteBuffer buf : buffers)
        {
            buffer.put(buf);
        }

        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap a collection of items
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapArray(Object[] value, Serializers serializers) throws IOException
    {
        final Object[] list = (Object[]) value;

        final ByteBuffer[] buffers = new ByteBuffer[list.length];

        int i = 0;
        int totalSize = 0;

        for(Object obj : list)
        {
            buffers[i] = wrap(obj, serializers);
            buffers[i].rewind();
            totalSize += buffers[i].limit();
            i++;
        }

        final ByteBuffer buffer = allocate(totalSize + Byte.BYTES + Integer.BYTES);

        buffer.put(ObjectType.ARRAY.getType());
        buffer.putInt(i); // Put the size of the array

        for(ByteBuffer buf : buffers)
        {
            buffer.put(buf);
        }

        buffer.rewind();
        return buffer;
    }

    /**
     * Wrap a collection of items
     *
     * @param value
     * @return
     */
    public static ByteBuffer wrapMap(Map value, Serializers serializers) throws IOException
    {
        final Map map = (Map) value;
        final Iterator<Map.Entry> iterator = map.entrySet().iterator();

        final ByteBuffer[] buffers = new ByteBuffer[map.size()*2];

        int i = 0;
        Map.Entry obj = null;
        int totalSize = 0;

        while(iterator.hasNext())
        {
            obj = iterator.next();
            buffers[i] = wrap(obj.getKey(), serializers);
            buffers[i+1] = wrap(obj.getValue(), serializers);
            buffers[i].rewind();
            buffers[i+1].rewind();
            totalSize += buffers[i].limit();
            totalSize += buffers[i+1].limit();
            i+=2;
        }

        final ByteBuffer buffer = allocate(totalSize + Byte.BYTES + Integer.BYTES);
        buffer.put(ObjectType.MAP.getType());
        buffer.putInt(i/2); // Put the size of the array

        for(ByteBuffer buf : buffers)
        {
            buffer.put(buf);
        }

        buffer.rewind();

        return buffer;
    }

    /**
     * Wrap Other
     *
     * @param value
     * @return
     * @throws java.io.IOException
     */
    public static ByteBuffer wrapOther(Object value) throws IOException
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(value);

        objectOutputStream.flush();
        objectOutputStream.close();

        byte[] bytes = byteArrayOutputStream.toByteArray();
        final ByteBuffer buffer = allocate(bytes.length + Byte.BYTES);
        buffer.put(ObjectType.OTHER.getType());
        buffer.put(bytes);

        buffer.rewind();
        return buffer;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Un-Wrap Methods
    //
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Unwrap with bytes
     *
     * @param bytes
     * @param serializers
     * @return
     * @throws IOException
     */
    public static Object unwrap(byte[] bytes, Serializers serializers) throws IOException
    {
        ByteBuffer buf = allocate(bytes.length);
        return unwrap(buf, serializers);
    }

    /**
     * Unwrap byte buffer into an object
     *
     * @param buffer
     * @return
     * @throws java.io.IOException
     */
    public static Object unwrap(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        final byte type = buffer.get();
        return unwrap(buffer, type, serializers);
    }

    /**
     * Unwrap byte buffer into an object
     *
     * @param buffer
     * @return
     * @throws java.io.IOException
     */
    public static Object unwrap(ByteBuffer buffer, Serializers serializers, int serializerId) throws IOException
    {
        final byte type = buffer.get();
        return unwrap(buffer, type, serializers, serializerId);
    }

    /**
     * Unwrap boolean
     *
     * @param buffer
     * @return
     */
    public static boolean unwrapBoolean(ByteBuffer buffer)
    {
        byte b = buffer.get();
        return (b == 2) ? false : true;
    }

    /**
     * Unwrap string
     *
     * @param buffer
     * @return
     */
    public static String unwrapString(ByteBuffer buffer)
    {
        int size = buffer.getInt();
        final byte[] stringBytes = new byte[size];
        buffer.get(stringBytes);
        return new String(stringBytes, CHARSET);
    }

    /**
     * Unwrap Class
     *
     * @param buffer
     * @return
     */
    public static Class unwrapClass(ByteBuffer buffer)
    {
        short classNameLength = buffer.getShort();

        byte[] classNameBytes = new byte[classNameLength];

        buffer.get(classNameBytes);

        final String className = new String(classNameBytes, CHARSET);
        try
        {
            return Class.forName(className);
        } catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unwrap string
     *
     * @param buffer
     * @return
     */
    public static Enum<?> unwrapEnum(ByteBuffer buffer) throws IOException
    {
        short classNameSize = buffer.getShort();

        byte[] stringBytes = new byte[classNameSize];
        buffer.get(stringBytes);
        String className = new String(stringBytes, CHARSET);

        short valueNameSize = buffer.getShort();
        stringBytes = new byte[valueNameSize];
        buffer.get(stringBytes);
        String enumName = new String(stringBytes, CHARSET);

        Class<Enum> enumClass = null;
        try
        {
            enumClass = (Class<Enum>)Class.forName(className);
        } catch (ClassNotFoundException e)
        {
            return null;
        }

        return Enum.valueOf(enumClass, enumName);

    }

    /**
     * Unwrap string
     *
     * @param buffer
     * @return
     */
    public static Date unwrapDate(ByteBuffer buffer)
    {
        return new Date(buffer.getLong());
    }

    /**
     * Unwrapped Named object from byte buffer to ByteBufferSerializable
     *
     * @param buffer
     * @param serializers
     * @return
     * @throws java.io.IOException
     */
    public static ObjectSerializable unwrapNamed(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        try
        {
            final short serializer = buffer.getShort();
            final ObjectSerializable serializable = (ObjectSerializable)serializers.getSerializerClass(serializer).newInstance();

            final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);

            serializable.readObject(objectBuffer);

            return serializable;
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Unwrapped Named object from byte buffer to ByteBufferSerializable
     *
     * @param buffer
     * @param serializers
     * @return
     * @throws java.io.IOException
     */
    public static ObjectSerializable unwrapNamed(ByteBuffer buffer, Serializers serializers, int serializerId) throws IOException
    {
        try
        {
            final short serializer = buffer.getShort();
            final ObjectSerializable serializable = (ObjectSerializable)serializers.getSerializerClass(serializer).newInstance();

            final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);

            serializable.readObject(objectBuffer, 0, serializerId);

            return serializable;
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Unwrap bytes
     *
     * @param buffer
     * @return
     */
    public static byte[] unwrapBytes(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * Unwrap map
     *
     * @param buffer
     * @param serializers
     * @return
     * @throws java.io.IOException
     */
    public static Map unwrapMap(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        final Map map = new HashMap();
        final int size = buffer.getInt();

        for(int i = 0; i < size; i++)
        {
            map.put(unwrap(buffer, serializers), unwrap(buffer, serializers));
        }

        return map;
    }

    public static Object unwrapNamed(ByteBuffer buffer, Class type) throws IOException
    {
        try
        {
            final ObjectSerializable serializable = (ObjectSerializable)type.newInstance();
            final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, null);
            serializable.readObject(objectBuffer);

            return serializable;
        } catch (InstantiationException e)
        {
            e.printStackTrace();
        } catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Unwrap other serializable or externalized object
     *
     * @param buffer
     * @return
     * @throws java.io.IOException
     */
    public static Object unwrapOther(ByteBuffer buffer) throws IOException
    {
        // Write the node using an ObjectOutputStream
        byte[] subBytes = new byte[buffer.limit() - buffer.position()];
        System.arraycopy(buffer.array(), buffer.position(), subBytes, 0, subBytes.length);
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(subBytes);
        final ObjectInputStream ois = new ObjectInputStream(byteArrayInputStream);

        try
        {
            final Object value = ois.readObject();
            return value;
        } catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        finally
        {
            buffer.position(buffer.position() + (subBytes.length - byteArrayInputStream.available()));

            ois.close();
            byteArrayInputStream.close();
        }

        return null;
    }

    /**
     * Un-wrapper for a collection
     *
     * @param buffer
     * @param type
     * @return
     * @throws java.io.IOException
     */
    protected static Collection unwrapCollection(ByteBuffer buffer, ObjectType type, Serializers serializers) throws IOException
    {
        Collection collection = null;

        if(type == ObjectType.HASH_SET)
        {
            collection = new HashSet();
        }
        else
        {
            collection = new ArrayList();
        }

        int size = buffer.getInt();

        for(int i = 0; i < size; i++)
        {
            collection.add(unwrap(buffer, serializers));
        }

        return collection;
    }

    /**
     * Un-wrapper for a collection
     *
     * @param buffer
     * @param serializers
     * @return
     * @throws java.io.IOException
     */
    protected static Object[] unwrapArray(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        Object[] collection = null;

        int size = buffer.getInt();

        collection = new Object[size];
        for(int i = 0; i < size; i++)
        {
            collection[i] = unwrap(buffer, serializers);
        }

        return collection;
    }

    public void reset()
    {
        buffer.rewind();
    }

    /**
     * Get Serializer ID for object.  This only applies to managed entities.  It will return the version id of the System Entity
     *
     * @param value
     * @return
     */
    public int getSerializerId(Object value)
    {
        if(value instanceof ManagedEntity && this.serializers != null && this.serializers.context != null)
        {
            try {
                return this.serializers.context.getSystemEntityByName(value.getClass().getCanonicalName()).getPrimaryKey();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Converts the buffer to a key value map.  Note this is intended to use only with ManagedEntities
     *
     * @param serializerId serializer id to use
     * @return Map representation of the object
     */
    public Map toMap(int serializerId)
    {
        Map<String, Object> results = new HashMap();

        // Read the type and serializer id to put the position in the right place to read attributes
        buffer.get();
        buffer.getShort();

        SystemEntity systemEntity = serializers.context.getSystemEntityById(serializerId);

        for (SystemAttribute attribute : systemEntity.getAttributes())
        {
            Object obj = null;
            try {
                obj = this.readObject();
            } catch (IOException e) {
                e.printStackTrace();
            }
            results.put(attribute.getName(), obj);
        }

        return results;
    }

    /**
     * Get A specific attribute from an object within the buffer.  Must have a SystemEntity serializer id
     * @param attributeName Attribute name from entity
     * @param serializerId serializer id that is a reference to a SystemEntity
     *
     * @return Attribute value from buffer
     */
    public Object getAttribute(String attributeName, int serializerId)
    {
        SystemEntity systemEntity = serializers.context.getSystemEntityById(serializerId);

        this.buffer.position(3);

        for (SystemAttribute attribute : systemEntity.getAttributes())
        {
            Object obj = null;
            try {
                obj = this.readObject();

                if(attribute.hashCode() == attributeName.hashCode()
                    && attribute.getName().equals(attributeName))
                    return obj;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}
