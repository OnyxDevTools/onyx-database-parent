package com.onyx.structure.serializer;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.structure.node.BitMapNode;
import com.onyx.structure.node.Record;
import com.onyx.structure.node.RecordReference;

import java.io.*;
import java.lang.reflect.Array;
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
        return buffer.limit();
    }

    /**
     * Truncate the buffer and return it
     *
     * @return
     */
    public ByteBuffer getByteBuffer()
    {
        buffer.limit(buffer.position());
        buffer.rewind();
        return buffer;
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
    public byte readByte()
    {
        return buffer.get();
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
        return (b == 1);
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
            if(buffer.hasRemaining())
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

    protected void ensureCapacity(int more)
    {
        if(buffer.capacity() < more + buffer.position())
        {
            ByteBuffer tempBuffer = allocate(buffer.limit() + more + BUFFER_ALLOCATION);
            buffer.limit(buffer.position());
            buffer.rewind();
            tempBuffer.put(buffer);

            this.buffer = tempBuffer;
        }
    }

    /**
     * Helper for writing anything to a buffer which is dynamically growing
     *
     * @param object
     * @throws java.io.IOException
     */
    public int writeObject(Object object) throws IOException
    {
        int currentPosition = buffer.position();
        wrap(object, serializers);
        return buffer.position() - currentPosition;
    }

    public void write(ObjectBuffer addBuffer)
    {
        final ByteBuffer bufferToAdd = addBuffer.getByteBuffer();

        ByteBuffer tempBuffer = null;
        if(buffer.capacity() < (bufferToAdd.limit() + buffer.position()))
        {
            tempBuffer = allocate(buffer.limit() + bufferToAdd.limit() + BUFFER_ALLOCATION);

            buffer.limit(buffer.position());
            buffer.rewind();
            tempBuffer.put(buffer);
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
        ensureCapacity(Short.BYTES);
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
        ensureCapacity(Integer.BYTES);
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
        ensureCapacity(Byte.BYTES);
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
        ensureCapacity((Byte.BYTES * val.length) + Integer.BYTES);
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
        ensureCapacity(Long.BYTES);
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
        ensureCapacity(Byte.BYTES);
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
        ensureCapacity(Long.BYTES);
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
        ensureCapacity(Float.BYTES);
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
        ensureCapacity(Double.BYTES);
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
        ensureCapacity(Long.BYTES * values.length);
        for(long val : values)
            buffer.putLong(val);
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     * @param count
     * @return
     */
    public static ByteBuffer allocate(int count)
    {
        ByteBuffer buffer = ByteBuffer.allocate(count);
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

        else if(type == ObjectType.FLOATS.getType())
            return unwrapFloats(buffer);
        else if(type == ObjectType.SHORTS.getType())
            return unwrapShorts(buffer);
        else if(type == ObjectType.BOOLEANS.getType())
            return unwrapBooleans(buffer);
        else if(type == ObjectType.DOUBLES.getType())
            return unwrapDoubles(buffer);
        else if(type == ObjectType.INTS.getType())
            return unwrapInts(buffer);
        else if(type == ObjectType.LONGS.getType())
            return unwrapLongs(buffer);
        else if(type == ObjectType.CHARS.getType())
            return unwrapChars(buffer);

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
    public int wrap(Object value, Serializers serializers) throws IOException
    {
        if(value == null)
            return wrapNull();
        else if(value instanceof BitMapNode)
            return wrapNamed(value, ObjectType.NODE);
        else if(value instanceof RecordReference)
            return wrapNamed(value, ObjectType.RECORD_REFERENCE);
        else if(value instanceof Record)
            return wrapNamed(value, ObjectType.RECORD);
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
        else if(value instanceof short[])
            return wrapShorts((short[]) value);
        else if(value instanceof boolean[])
            return wrapBooleans((boolean[]) value);
        else if(value instanceof double[])
            return wrapDoubles((double[]) value);
        else if(value instanceof int[])
            return wrapInts((int[]) value);
        else if(value instanceof long[])
            return wrapLongs((long[]) value);
        else if(value instanceof char[])
            return wrapChars((char[]) value);
        else if(value instanceof float[])
            return wrapFloats((float[]) value);
        else if(value instanceof String)
            return wrapString((String) value);
        else if(value.getClass().isArray())
            return wrapArray(value);
        else if(value instanceof Collection)
            return wrapCollection((Collection) value);
        else if(value instanceof Map)
            return wrapMap((Map)value);
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
    public int wrapNull()
    {
        ensureCapacity(1);
        buffer.put(ObjectType.NULL.getType());
        return 1;
    }

    /**
     * Wrap Long
     *
     * @param value
     * @return
     */
    public int wrapLong(long value)
    {
        ensureCapacity(Long.BYTES + Byte.BYTES);

        buffer.put(ObjectType.LONG.getType());
        buffer.putLong(value);
        return  Long.BYTES + Byte.BYTES;
    }

    /**
     * Wrap Class
     *
     * @param value
     * @return
     */
    public int wrapClass(Class value)
    {

        byte[] classNameBytes = value.getName().getBytes(CHARSET);
        short classNameLength = (short)value.getName().length();

        ensureCapacity(Short.BYTES + Byte.BYTES + classNameBytes.length);

        buffer.put(ObjectType.CLASS.getType());
        buffer.putShort(classNameLength);
        buffer.put(classNameBytes);
        return Short.BYTES + Byte.BYTES + classNameBytes.length;
    }

    /**
     * Wrap Long
     *
     * @param value
     * @return
     */
    public int wrapDate(Date value)
    {
        ensureCapacity(Long.BYTES + Byte.BYTES);
        buffer.put(ObjectType.DATE.getType());
        buffer.putLong(value.getTime());
        return Long.BYTES + Byte.BYTES;
    }

    /**
     * Wrap Short
     *
     * @param value
     * @return
     */
    public int wrapShort(short value)
    {
        ensureCapacity(Byte.BYTES + Short.BYTES);
        buffer.put(ObjectType.SHORT.getType());
        buffer.putShort(value);
        return Byte.BYTES + Short.BYTES;
    }

    /**
     * Wrap Int
     *
     * @param value
     * @return
     */
    public int wrapInt(int value)
    {
        ensureCapacity(Byte.BYTES + Integer.BYTES);
        buffer.put(ObjectType.INT.getType());
        buffer.putInt(value);
        return Byte.BYTES + Integer.BYTES;
    }

    /**
     * Wrap Int
     *
     * @param value
     * @return
     */
    public int wrapChar(char value)
    {
        ensureCapacity(Byte.BYTES + 2);

        buffer.put(ObjectType.CHAR.getType());
        buffer.putChar(value);
        return Byte.BYTES + 2;
    }

    /**
     * Wrap Int
     *
     * @param value
     * @return
     */
    public int wrapByte(byte value)
    {
        ensureCapacity(Byte.BYTES + Byte.BYTES);

        buffer.put(ObjectType.BYTE.getType());
        buffer.put(value);
        return Byte.BYTES + Byte.BYTES;
    }

    /**
     * Wrap Double
     *
     * @param value
     * @return
     */
    public int wrapDouble(double value)
    {
        ensureCapacity(Byte.BYTES + Double.BYTES);
        buffer.put(ObjectType.DOUBLE.getType());
        buffer.putDouble(value);
        return Byte.BYTES + Double.BYTES;
    }

    /**
     * Wrap Boolean
     *
     * @param value
     * @return
     */
    public int wrapBoolean(boolean value)
    {
        ensureCapacity(Byte.BYTES + 1);
        buffer.put(ObjectType.BOOLEAN.getType());
        buffer.put((value == true) ? (byte)1 : (byte)2);
        return Byte.BYTES + 1;
    }

    /**
     * Wrap Float
     *
     * @param value
     * @return
     */
    public int wrapFloat(float value)
    {
        ensureCapacity(Byte.BYTES + Float.BYTES);
        buffer.put(ObjectType.FLOAT.getType());
        buffer.putFloat(value);
        return Byte.BYTES + Float.BYTES;
    }

    /**
     * Wrap Byte Array
     *
     * @param value
     * @return
     */
    public int wrapBytes(byte[] value)
    {
        int length = (Byte.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.BYTES.getType());
        buffer.putInt(value.length);
        buffer.put(value);
        return length;
    }

    /**
     * Wrap short Array
     *
     * @param value array of shorts
     * @return number of bytes written
     */
    public int wrapShorts(short[] value)
    {
        int length = (Short.BYTES * value.length) + Integer.BYTES + Byte.BYTES;
        ensureCapacity(length);
        buffer.put(ObjectType.SHORTS.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.putShort(value[i]);
        return length;
    }

    /**
     * Wrap boolean Array
     *
     * @param value array of boolean
     * @return number of bytes written
     */
    public int wrapBooleans(boolean[] value)
    {
        int length = (Byte.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.BOOLEANS.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.put(value[i] ? (byte) 1 : (byte) 0);
        return length;
    }

    /**
     * Wrap double Array
     *
     * @param value array of double
     * @return number of bytes written
     */
    public int wrapDoubles(double[] value)
    {
        int length = (Double.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.DOUBLES.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.putDouble(value[i]);
        return length;
    }

    /**
     * Wrap int Array
     *
     * @param value array of int
     * @return number of bytes written
     */
    public int wrapInts(int[] value)
    {
        int length = (Integer.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.INTS.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.putInt(value[i]);
        return length;
    }

    /**
     * Wrap long Array
     *
     * @param value array of long
     * @return number of bytes written
     */
    public int wrapLongs(long[] value)
    {
        int length = (Long.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.LONGS.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.putLong(value[i]);
        return length;
    }

    /**
     * Wrap char Array
     *
     * @param value array of char
     * @return number of bytes written
     */
    public int wrapChars(char[] value)
    {
        int length = (Long.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.CHARS.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.putChar(value[i]);
        return length;
    }

    /**
     * Wrap float Array
     *
     * @param value array of floats
     * @return number of bytes written
     */
    public int wrapFloats(float[] value)
    {
        int length = (Float.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.FLOATS.getType());
        buffer.putInt(value.length);

        for(int i = 0; i < value.length; i++)
            buffer.putFloat(value[i]);
        return length;
    }

    /**
     * Wrap String
     *
     * @param value
     * @return
     */
    public int wrapString(String value)
    {
        final int length = value.length();
        final byte[] stringBytes = value.getBytes(CHARSET);

        ensureCapacity(Byte.BYTES + stringBytes.length + Integer.BYTES);

        buffer.put(ObjectType.STRING.getType());
        buffer.putInt(length);
        buffer.put(stringBytes);
        return Byte.BYTES + stringBytes.length + Integer.BYTES;
    }

    /**
     * Wrap Enum
     *
     * @param enumValue
     * @return
     */
    public int wrapEnum(Enum<?> enumValue)
    {
        final byte[] stringBytes = enumValue.getDeclaringClass().getName().getBytes(CHARSET);
        final byte[] nameBytes = enumValue.name().getBytes(CHARSET);

        ensureCapacity(Byte.BYTES + stringBytes.length + nameBytes.length + Short.BYTES + Short.BYTES);

        buffer.put(ObjectType.ENUM.getType());
        buffer.putShort((short) stringBytes.length);
        buffer.put(stringBytes);
        buffer.putShort((short) nameBytes.length);
        buffer.put(nameBytes);

        return Byte.BYTES + stringBytes.length + nameBytes.length + Short.BYTES + Short.BYTES;
    }

    /**
     * Wrap a named object that implements ByteBufferSerializable
     *
     * @param value
     * @param serializers
     * @return
     * @throws java.io.IOException
     */
    private int wrapNamed(Object value, Serializers serializers) throws IOException
    {

        int bufferPosition = buffer.position();
        Short customType = serializers.getSerializerId(value.getClass().getName());

        // Create a new custom serializer
        if(customType == null)
        {
            customType = serializers.add(value.getClass().getName());
        }

        ensureCapacity(Byte.BYTES + Short.BYTES);
        buffer.put(ObjectType.BUFFER_OBJ.getType());
        buffer.putShort(customType);

        ((ObjectSerializable)value).writeObject(this);

        return buffer.position() - bufferPosition;
    }

    /**
     * Wrap a named object that implements ByteBufferSerializable
     *
     * @param value
     * @param type
     * @return
     * @throws java.io.IOException
     */
    private int wrapNamed(Object value, ObjectType type) throws IOException
    {
        int bufferPosition = buffer.position();

        ensureCapacity(Byte.BYTES);
        buffer.put(type.getType());
        ((ObjectSerializable)value).writeObject(this);

        return buffer.position() - bufferPosition;
    }

    /**
     * Wrap a collection of items
     *
     * @param value
     * @return
     */
    public int wrapCollection(Collection value) throws IOException
    {
        int bufferPosition = buffer.position();
        final Iterator iterator = value.iterator();

        Object obj = null;

        ensureCapacity(Byte.BYTES + Integer.BYTES);

        if(value instanceof HashSet)
        {
            buffer.put(ObjectType.HASH_SET.getType());
        }
        else
        {
            buffer.put(ObjectType.COLLECTION.getType());
        }

        buffer.putInt(value.size()); // Put the size of the array

        while(iterator.hasNext())
        {
            obj = iterator.next();
            writeObject(obj);
        }

        return buffer.position() - bufferPosition;
    }

    /**
     * Wrap a collection of items
     *
     * @param value
     * @return
     */
    public int wrapArray(Object value) throws IOException
    {
        int bufferPosition = buffer.position();

        ensureCapacity(Byte.BYTES + Integer.BYTES);

        int length = Array.getLength(value);
        buffer.put(ObjectType.ARRAY.getType());
        buffer.putInt(length); // Put the size of the array

        for(int i = 0; i < length; i++)
        {
            writeObject(Array.get(value, i));
        }

        return buffer.position() - bufferPosition;
    }

    /**
     * Wrap a collection of items
     *
     * @param value
     * @return
     */
    public int wrapMap(Map value) throws IOException
    {
        int bufferPosition = buffer.position();

        final Iterator<Map.Entry> iterator = value.entrySet().iterator();

        Map.Entry obj = null;

        ensureCapacity(Byte.BYTES + Integer.BYTES);

        buffer.put(ObjectType.MAP.getType());
        buffer.putInt(value.size()); // Put the size of the array

        while(iterator.hasNext())
        {
            obj = iterator.next();
            writeObject(obj.getKey());
            writeObject(obj.getValue());
        }

        return buffer.position() - bufferPosition;
    }

    /**
     * Wrap Other
     *
     * @param value
     * @return
     * @throws java.io.IOException
     */
    public int wrapOther(Object value) throws IOException
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(value);

        objectOutputStream.flush();
        objectOutputStream.close();

        byte[] bytes = byteArrayOutputStream.toByteArray();

        ensureCapacity(bytes.length + Byte.BYTES);
        buffer.put(ObjectType.OTHER.getType());
        buffer.put(bytes);

        return bytes.length + Byte.BYTES;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Un-Wrap Methods
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


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
        return b != 2;
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
     * Unwrap array of floats
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static float[] unwrapFloats(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final float[] array = new float[size];
        for(int i = 0; i < size; i++)
            array[i] = buffer.getFloat();
        return array;
    }

    /**
     * Unwrap array of short
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static short[] unwrapShorts(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final short[] array = new short[size];
        for(int i = 0; i < size; i++)
            array[i] = buffer.getShort();
        return array;
    }

    /**
     * Unwrap array of boolean
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static boolean[] unwrapBooleans(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final boolean[] array = new boolean[size];
        for(int i = 0; i < size; i++)
            array[i] = (buffer.get() == 1);
        return array;
    }

    /**
     * Unwrap array of double
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static double[] unwrapDoubles(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final double[] array = new double[size];
        for(int i = 0; i < size; i++)
            array[i] = buffer.getDouble();
        return array;
    }

    /**
     * Unwrap array of int
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static int[] unwrapInts(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final int[] array = new int[size];
        for(int i = 0; i < size; i++)
            array[i] = buffer.getInt();
        return array;
    }

    /**
     * Unwrap array of long
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static long[] unwrapLongs(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final long[] array = new long[size];
        for(int i = 0; i < size; i++)
            array[i] = buffer.getLong();
        return array;
    }

    /**
     * Unwrap array of char
     *
     * @param buffer Input Buffer
     * @return The array read from the buffer
     */
    public static char[] unwrapChars(ByteBuffer buffer)
    {
        final int size = buffer.getInt();
        final char[] array = new char[size];
        for(int i = 0; i < size; i++)
            array[i] = buffer.getChar();
        return array;
    }

    /**
     * Unwrap structure
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
        int originalPosition = buffer.position();

        // Write the node using an ObjectOutputStream
        byte[] subBytes = new byte[buffer.limit() - buffer.position()];
        buffer.get(subBytes);

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
            buffer.position(originalPosition + (subBytes.length - byteArrayInputStream.available()));

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
                return this.serializers.context.getSystemEntityByName(value.getClass().getName()).getPrimaryKey();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Converts the buffer to a key key structure.  Note this is intended to use only with ManagedEntities
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
     * @return Attribute key from buffer
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

                if(attribute.getName().hashCode() == attributeName.hashCode()
                        && attribute.getName().equals(attributeName))
                    return obj;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }
}
