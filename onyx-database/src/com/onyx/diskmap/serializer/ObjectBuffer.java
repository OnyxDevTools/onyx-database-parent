package com.onyx.diskmap.serializer;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.persistence.ManagedEntity;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by timothy.osborn on 3/22/15.
 *
 * This class is a legacy serializer.  It has since been replaced by the BufferStream class
 */
public class ObjectBuffer
{

    private static final Charset CHARSET = Charset.forName("UTF-8"); // Supported Character set

    private static final int BUFFER_ALLOCATION = 88; // Initial Buffer allocation size

    public final Serializers serializers;

    @SuppressWarnings("WeakerAccess")
    protected ByteBuffer buffer = null;

    /**
     * Constructor with serializers
     *
     * @param serializers Custom serializers
     */
    public ObjectBuffer(Serializers serializers)
    {
        this.serializers = serializers;
        this.buffer = allocate(BUFFER_ALLOCATION);
    }

    /**
     * Constructor with initial byte buffer and serializers
     *
     * @param buffer Underlying ByteBuffer to put or read from
     * @param serializers Custom serializers
     */
    public ObjectBuffer(ByteBuffer buffer, Serializers serializers)
    {
        this.serializers = serializers;
        this.buffer = buffer;
    }

    /**
     * Get the size of the buffer
     * @return Integer value
     */
    @SuppressWarnings("unused")
    public int getSize()
    {
        return buffer.limit();
    }

    /**
     * Truncate the buffer and return it
     *
     * @return The byte buffer being used.  This will also truncate and limit the value
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
     * @param type Object Type to read
     * @return The object read
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer.  Typically overflow.
     */
    @SuppressWarnings("unused")
    public Object read(ObjectType type) throws IOException
    {
        return unwrap(buffer, type.getType(), serializers);
    }

    /**
     * Read Int
     *
     * @return Int from buffer
     */
    public int readInt()
    {
        return buffer.getInt();
    }

    /**
     * Read Date
     *
     * @return Date from buffer
     */
    @SuppressWarnings("unused")
    public Date readDate()
    {
        return new Date(buffer.getLong());
    }

    /**
     * Read Long
     *
     * @return Long from buffer
     */
    public long readLong()
    {
        return buffer.getLong();
    }

    /**
     * Read Long
     *
     * @return double from buffer
     */
    @SuppressWarnings("unused")
    public double readDouble()
    {
        return buffer.getDouble();
    }

    /**
     * Read Long
     *
     * @return float from buffer
     */
    @SuppressWarnings("unused")
    public float readFloat()
    {
        return buffer.getFloat();
    }

    /**
     * Read Long
     *
     * @return byte from buffer
     */
    public byte readByte()
    {
        return buffer.get();
    }

    /**
     * Read byte array
     *
     * @return byte array from buffer
     */
    @SuppressWarnings("unused")
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
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from byte buffer
     */
    @SuppressWarnings("RedundantThrows")
    public boolean readBoolean() throws IOException
    {
        byte b = buffer.get();
        return (b == 1);
    }

    /**
     * Read Long Array
     * @param size Size of long array
     * @return array of longs
     */
    public long[] readLongArray(@SuppressWarnings("SameParameterValue") int size)
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
     * @return short from buffer
     */
    @SuppressWarnings("unused")
    public short readShort()
    {
        return buffer.getShort();
    }

    /**
     * Ensure the capacity is large enough when writing to a byte buffer
     * @param more How many more bytes you need
     */
    private void ensureCapacity(int more)
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
     * @param object Object to serialize
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue writing to ByteBuffer
     */
    public int writeObject(Object object) throws IOException
    {
        int currentPosition = buffer.position();
        wrap(object, serializers);
        return buffer.position() - currentPosition;
    }

    /**
     * Write another buffer onto the existing buffer
     * @param addBuffer Buffer to add
     */
    @SuppressWarnings("unused")
    public void write(ObjectBuffer addBuffer)
    {
        final ByteBuffer bufferToAdd = addBuffer.getByteBuffer();

        ByteBuffer tempBuffer;
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
     * @return An object that is de-serialized from buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer
     */
    public Object readObject() throws IOException
    {
        return unwrap(buffer, serializers);
    }

    /**
     * Write Short
     *
     * @param val Short value to write
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unused")
    public void writeShort(short val) throws IOException
    {
        ensureCapacity(Short.BYTES);
        buffer.putShort(val);
    }

    /**
     * Write Int
     *
     * @param val Integer to write to buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("RedundantThrows")
    public void writeInt(int val) throws IOException
    {
        ensureCapacity(Integer.BYTES);
        buffer.putInt(val);
    }

    /**
     * Write Byte
     *
     * @param val Byte to write to buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("RedundantThrows")
    public void writeByte(byte val) throws IOException
    {
        ensureCapacity(Byte.BYTES);
        buffer.put(val);
    }

    /**
     * Write Byte array
     *
     * @param val byte array to write to buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unused")
    public void writeBytes(byte[] val) throws IOException
    {
        ensureCapacity((Byte.BYTES * val.length) + Integer.BYTES);
        buffer.putInt(val.length);
        buffer.put(val);
    }

    /**
     * Write Long
     *
     * @param val long to write
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("RedundantThrows")
    public void writeLong(long val) throws IOException
    {
        ensureCapacity(Long.BYTES);
        buffer.putLong(val);
    }

    /**
     * Write Boolean
     *
     * @param val boolean to write
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer  
     */
    @SuppressWarnings("RedundantThrows")
    public void writeBoolean(boolean val) throws IOException
    {
        ensureCapacity(Byte.BYTES);
        buffer.put((val) ? (byte) 1 : (byte) 2);
    }

    /**
     * Write Date
     *
     * @param val date to write
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unused")
    public void writeDate(Date val) throws IOException
    {
        ensureCapacity(Long.BYTES);
        buffer.putLong(val.getTime());
    }

    /**
     * Write Date
     *
     * @param val float to write to buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unused")
    public void writeFloat(Float val) throws IOException
    {
        ensureCapacity(Float.BYTES);
        buffer.putFloat(val);
    }

    /**
     * Write Date
     *
     * @param val double to write to buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unused")
    public void writeDouble(Double val) throws IOException
    {
        ensureCapacity(Double.BYTES);
        buffer.putDouble(val);
    }

    /**
     * Write Long Array
     *
     * @param values long array to write to buffer
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("RedundantThrows")
    public void writeLongArray(long[] values) throws IOException
    {
        ensureCapacity(Long.BYTES * values.length);
        for(long val : values)
            buffer.putLong(val);
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     * @param count amount of bytes to allocate
     * @return The allocated byte buffer.
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
     * @param buffer Buffer to read from
     * @return Object that was unwrapped
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private static Object unwrap(ByteBuffer buffer, byte type, Serializers serializers) throws IOException
    {
        ObjectType objectType = ObjectType.values()[type];
        switch (objectType)
        {
            case NULL:
                return null;
            case LONG:
                return buffer.getLong();
            case INT:
                return buffer.getInt();
            case SHORT:
                return buffer.getShort();
            case DOUBLE:
                return buffer.getDouble();
            case FLOAT:
                return buffer.getFloat();
            case BOOLEAN:
                return unwrapBoolean(buffer);
            case STRING:
                return unwrapString(buffer);
            case BUFFER_OBJ:
                return unwrapNamed(buffer, serializers);
            case BYTES:
                return unwrapBytes(buffer);
            case HASH_SET:
                return unwrapCollection(buffer, ObjectType.HASH_SET, serializers);
            case COLLECTION:
                return unwrapCollection(buffer, ObjectType.COLLECTION, serializers);
            case MAP:
                return unwrapMap(buffer, serializers);
            case DATE:
                return unwrapDate(buffer);
            case OTHER:
                return unwrapOther(buffer);
            case ENUM:
                return unwrapEnum(buffer);
            case ARRAY:
                return unwrapArray(buffer, serializers);
            case CHAR:
                return buffer.getChar();
            case BYTE:
                return buffer.get();
            case FLOATS:
                return unwrapFloats(buffer);
            case SHORTS:
                return unwrapShorts(buffer);
            case BOOLEANS:
                return unwrapBooleans(buffer);
            case DOUBLES:
                return unwrapDoubles(buffer);
            case INTS:
                return unwrapInts(buffer);
            case LONGS:
                return unwrapLongs(buffer);
            case CHARS:
                return unwrapChars(buffer);
        }

        return null;
    }

    /**
     * Unwrap byte buffer into an object
     *
     * @param buffer Buffer to read from
     * @return Object that was unwrapped
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private static Object unwrap(ByteBuffer buffer, byte type, Serializers serializers, int serializerId) throws IOException
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
     * @param value Value to serialize and wrap
     * @return how many bytes were written
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    private int wrap(Object value, Serializers serializers) throws IOException
    {
        if(value == null)
            return wrapNull();
        else if(value instanceof ObjectSerializable)
            return wrapNamed(value, serializers);
        else if(value instanceof String)
            return wrapString((String)value);
        else if(value instanceof Long)
            return wrapLong((Long)value);
        else if(value instanceof Short)
            return wrapShort((Short) value);
        else if(value instanceof Date)
            return wrapDate((Date)value);
        else if(value instanceof Integer)
            return wrapInt((Integer)value);
        else if(value instanceof Character)
            return wrapChar((Character)value);
        else if(value instanceof Byte)
            return wrapByte((Byte)value);
        else if(value instanceof Double)
            return wrapDouble((Double)value);
        else if(value instanceof Float)
            return wrapFloat((Float)value);
        else if(value instanceof Boolean)
            return wrapBoolean((Boolean)value);
        else if(value instanceof Collection)
            return wrapCollection((Collection)value);
        else if(value instanceof Map)
            return wrapMap((Map)value);
        else {
            final Class clazz = value.getClass();
            if (clazz.isEnum())
                return wrapEnum((Enum)value);
            else if (clazz.isArray()) {
                if (value instanceof byte[])
                    return wrapBytes((byte[]) value);
                else if (value instanceof short[])
                    return wrapShorts((short[]) value);
                else if (value instanceof boolean[])
                    return wrapBooleans((boolean[]) value);
                else if (value instanceof double[])
                    return wrapDoubles((double[]) value);
                else if (value instanceof int[])
                    return wrapInts((int[]) value);
                else if (value instanceof long[])
                    return wrapLongs((long[]) value);
                else if (value instanceof char[])
                    return wrapChars((char[]) value);
                else if (value instanceof float[])
                    return wrapFloats((float[]) value);
                else
                    return wrapArray(value);
            } else
                return wrapOther(value);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Wrap Methods
    //
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Wrap Null
     *
     * @return the amount of bytes written to buffer
     */
    @SuppressWarnings("SameReturnValue")
    private int wrapNull()
    {
        ensureCapacity(1);
        buffer.put(ObjectType.NULL.getType());
        return 1;
    }

    /**
     * Wrap Long
     *
     * @param value long to write
     * @return the amount of bytes written to buffer
     */
    private int wrapLong(long value)
    {
        ensureCapacity(Long.BYTES + Byte.BYTES);

        buffer.put(ObjectType.LONG.getType());
        buffer.putLong(value);
        return  Long.BYTES + Byte.BYTES;
    }

    /**
     * Wrap Long
     *
     * @param value date to write
     * @return the amount of bytes written to buffer
     */
    private int wrapDate(Date value)
    {
        ensureCapacity(Long.BYTES + Byte.BYTES);
        buffer.put(ObjectType.DATE.getType());
        buffer.putLong(value.getTime());
        return Long.BYTES + Byte.BYTES;
    }

    /**
     * Wrap Short
     *
     * @param value short to write
     * @return the amount of bytes written to buffer
     */
    private int wrapShort(short value)
    {
        ensureCapacity(Byte.BYTES + Short.BYTES);
        buffer.put(ObjectType.SHORT.getType());
        buffer.putShort(value);
        return Byte.BYTES + Short.BYTES;
    }

    /**
     * Wrap Int
     *
     * @param value int to write
     * @return the amount of bytes written to buffer
     */
    private int wrapInt(int value)
    {
        ensureCapacity(Byte.BYTES + Integer.BYTES);
        buffer.put(ObjectType.INT.getType());
        buffer.putInt(value);
        return Byte.BYTES + Integer.BYTES;
    }

    /**
     * Wrap Char
     *
     * @param value char to write
     * @return the amount of bytes written to buffer
     */
    private int wrapChar(char value)
    {
        ensureCapacity(Byte.BYTES + 2);

        buffer.put(ObjectType.CHAR.getType());
        buffer.putChar(value);
        return Byte.BYTES + 2;
    }

    /**
     * Wrap Int
     *
     * @param value byte to write
     * @return the amount of bytes written to buffer
     */
    private int wrapByte(byte value)
    {
        ensureCapacity(Byte.BYTES + Byte.BYTES);

        buffer.put(ObjectType.BYTE.getType());
        buffer.put(value);
        return Byte.BYTES + Byte.BYTES;
    }

    /**
     * Wrap Double
     *
     * @param value double to write
     * @return the amount of bytes written to buffer
     */
    private int wrapDouble(double value)
    {
        ensureCapacity(Byte.BYTES + Double.BYTES);
        buffer.put(ObjectType.DOUBLE.getType());
        buffer.putDouble(value);
        return Byte.BYTES + Double.BYTES;
    }

    /**
     * Wrap Boolean
     *
     * @param value boolean to write
     * @return the amount of bytes written to buffer
     */
    private int wrapBoolean(boolean value)
    {
        ensureCapacity(Byte.BYTES + 1);
        buffer.put(ObjectType.BOOLEAN.getType());
        buffer.put((value) ? (byte)1 : (byte)2);
        return Byte.BYTES + 1;
    }

    /**
     * Wrap Float
     *
     * @param value float to write
     * @return the amount of bytes written to buffer
     */
    private int wrapFloat(float value)
    {
        ensureCapacity(Byte.BYTES + Float.BYTES);
        buffer.put(ObjectType.FLOAT.getType());
        buffer.putFloat(value);
        return Byte.BYTES + Float.BYTES;
    }

    /**
     * Wrap Byte Array
     *
     * @param value byte array to write
     * @return the amount of bytes written to buffer
     */
    private int wrapBytes(byte[] value)
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
    private int wrapShorts(short[] value)
    {
        int length = (Short.BYTES * value.length) + Integer.BYTES + Byte.BYTES;
        ensureCapacity(length);
        buffer.put(ObjectType.SHORTS.getType());
        buffer.putInt(value.length);

        for (short aValue : value) buffer.putShort(aValue);
        return length;
    }

    /**
     * Wrap boolean Array
     *
     * @param value array of boolean
     * @return number of bytes written
     */
    private int wrapBooleans(boolean[] value)
    {
        int length = (Byte.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.BOOLEANS.getType());
        buffer.putInt(value.length);

        for (boolean aValue : value) buffer.put(aValue ? (byte) 1 : (byte) 0);
        return length;
    }

    /**
     * Wrap double Array
     *
     * @param value array of double
     * @return number of bytes written
     */
    private int wrapDoubles(double[] value)
    {
        int length = (Double.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.DOUBLES.getType());
        buffer.putInt(value.length);

        for (double aValue : value) buffer.putDouble(aValue);
        return length;
    }

    /**
     * Wrap int Array
     *
     * @param value array of int
     * @return number of bytes written
     */
    private int wrapInts(int[] value)
    {
        int length = (Integer.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.INTS.getType());
        buffer.putInt(value.length);

        for (int aValue : value) buffer.putInt(aValue);
        return length;
    }

    /**
     * Wrap long Array
     *
     * @param value array of long
     * @return number of bytes written
     */
    private int wrapLongs(long[] value)
    {
        int length = (Long.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.LONGS.getType());
        buffer.putInt(value.length);

        for (long aValue : value) buffer.putLong(aValue);
        return length;
    }

    /**
     * Wrap char Array
     *
     * @param value array of char
     * @return number of bytes written
     */
    private int wrapChars(char[] value)
    {
        int length = (Long.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.CHARS.getType());
        buffer.putInt(value.length);

        for (char aValue : value) buffer.putChar(aValue);
        return length;
    }

    /**
     * Wrap float Array
     *
     * @param value array of floats
     * @return number of bytes written
     */
    private int wrapFloats(float[] value)
    {
        int length = (Float.BYTES * value.length) + Integer.BYTES + Byte.BYTES;

        ensureCapacity(length);
        buffer.put(ObjectType.FLOATS.getType());
        buffer.putInt(value.length);

        for (float aValue : value) buffer.putFloat(aValue);
        return length;
    }

    /**
     * Wrap String
     *
     * @param value string to write
     * @return how many bytes were written
     */
    private int wrapString(String value)
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
     * @param enumValue enum to write
     * @return How many bytes were written
     */
    private int wrapEnum(Enum<?> enumValue)
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
     * Wrap a named object that implements ObjectSerializable
     *
     * @param value Named object to write.  Must implement ObjectSerializable
     * @param serializers Custom serializers
     * @return How many bytes were written
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
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
     * Wrap a collection of items
     *
     * @param value Collection to write
     * @return How many bytes were written
     */
    private int wrapCollection(Collection value) throws IOException
    {
        int bufferPosition = buffer.position();
        final Iterator iterator = value.iterator();

        Object obj;

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
     * @param value Array to write
     * @return How many bytes were written
     */
    private int wrapArray(Object value) throws IOException
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
     * @param value Map to write
     * @return How many bytes were written
     */
    @SuppressWarnings("unchecked")
    private int wrapMap(Map value) throws IOException
    {
        int bufferPosition = buffer.position();

        final Iterator<Map.Entry> iterator = value.entrySet().iterator();

        Map.Entry obj;

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
     * @param value Serializable object to write
     * @return How many bytes were written
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private int wrapOther(Object value) throws IOException
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
     * @param buffer Buffer to read from
     * @return How many bytes were written
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    public static Object unwrap(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        final byte type = buffer.get();
        return unwrap(buffer, type, serializers);
    }

    /**
     * Unwrap byte buffer into an object
     *
     * @param buffer Buffer to read from
     * @return How many bytes were written
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    public static Object unwrap(ByteBuffer buffer, Serializers serializers, int serializerId) throws IOException
    {
        final byte type = buffer.get();
        return unwrap(buffer, type, serializers, serializerId);
    }

    /**
     * Unwrap boolean
     *
     * @param buffer Buffer to read from
     * @return unwrapped boolean
     */
    private static boolean unwrapBoolean(ByteBuffer buffer)
    {
        byte b = buffer.get();
        return b != 2;
    }

    /**
     * Unwrap string
     *
     * @param buffer Buffer to read from
     * @return unwrapped string
     */
    private static String unwrapString(ByteBuffer buffer)
    {
        int size = buffer.getInt();
        final byte[] stringBytes = new byte[size];
        buffer.get(stringBytes);
        return new String(stringBytes, CHARSET);
    }

    /**
     * Unwrap string
     *
     * @param buffer Buffer to read from
     * @return unwrapped enum
     */
    @SuppressWarnings({"unchecked", "RedundantThrows"})
    private static Enum<?> unwrapEnum(ByteBuffer buffer) throws IOException
    {
        short classNameSize = buffer.getShort();

        byte[] stringBytes = new byte[classNameSize];
        buffer.get(stringBytes);
        String className = new String(stringBytes, CHARSET);

        short valueNameSize = buffer.getShort();
        stringBytes = new byte[valueNameSize];
        buffer.get(stringBytes);
        String enumName = new String(stringBytes, CHARSET);

        Class<Enum> enumClass;
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
     * @param buffer Buffer to read from
     * @return unwrapped date
     */
    private static Date unwrapDate(ByteBuffer buffer)
    {
        return new Date(buffer.getLong());
    }

    /**
     * Unwrapped Named object from byte buffer to ByteBufferSerializable
     *
     * @param buffer Buffer to read from
     * @param serializers custom serializers
     * @return unwrapped ObjectSerializable
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private static ObjectSerializable unwrapNamed(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        try
        {
            final short serializer = buffer.getShort();
            final ObjectSerializable serializable = (ObjectSerializable)serializers.getSerializerClass(serializer).newInstance();

            final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);

            serializable.readObject(objectBuffer);
            return serializable;
        } catch (InstantiationException | IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Unwrapped Named object from byte buffer to ByteBufferSerializable
     *
     * @param buffer Buffer to read from
     * @param serializers custom serializers
     * @return unwrapped ObjectSerializable
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private static ObjectSerializable unwrapNamed(ByteBuffer buffer, Serializers serializers, int serializerId) throws IOException
    {
        try
        {
            final short serializer = buffer.getShort();
            final ObjectSerializable serializable = (ObjectSerializable)serializers.getSerializerClass(serializer).newInstance();

            final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);

            serializable.readObject(objectBuffer, 0, serializerId);

            return serializable;
        } catch (InstantiationException | IllegalAccessException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Unwrap bytes
     *
     * @param buffer Buffer to read from
     * @return unwrapped byte array
     */
    private static byte[] unwrapBytes(ByteBuffer buffer)
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
    private static float[] unwrapFloats(ByteBuffer buffer)
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
    private static short[] unwrapShorts(ByteBuffer buffer)
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
    private static boolean[] unwrapBooleans(ByteBuffer buffer)
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
    private static double[] unwrapDoubles(ByteBuffer buffer)
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
    private static int[] unwrapInts(ByteBuffer buffer)
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
    private static long[] unwrapLongs(ByteBuffer buffer)
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
    private static char[] unwrapChars(ByteBuffer buffer)
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
     * @param buffer buffer to read from
     * @param serializers custom serializers
     * @return unwrapped map
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unchecked")
    private static Map unwrapMap(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        final Map map = new HashMap();
        final int size = buffer.getInt();

        for(int i = 0; i < size; i++)
        {
            map.put(unwrap(buffer, serializers), unwrap(buffer, serializers));
        }

        return map;
    }

    /**
     * Unwrap other serializable or externalized object
     *
     * @param buffer buffer to read from
     * @return unwrapped serializable object
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private static Object unwrapOther(ByteBuffer buffer) throws IOException
    {
        int originalPosition = buffer.position();

        // Write the node using an ObjectOutputStream
        byte[] subBytes = new byte[buffer.limit() - buffer.position()];
        buffer.get(subBytes);

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(subBytes);
        final ObjectInputStream ois = new ObjectInputStream(byteArrayInputStream);

        try
        {
            return ois.readObject();
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
     * @param buffer buffer to read from
     * @param type Type of collection
     * @return Unwrapped collection
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    @SuppressWarnings("unchecked")
    private static Collection unwrapCollection(ByteBuffer buffer, ObjectType type, Serializers serializers) throws IOException
    {
        Collection collection;

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
     * @param buffer buffer to read from
     * @param serializers Custom serializers
     * @return Object array
     * @throws java.io.IOException Issue reading from buffer Issue reading from buffer
     */
    private static Object[] unwrapArray(ByteBuffer buffer, Serializers serializers) throws IOException
    {
        Object[] collection;

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
     * @param value object to check for serializer id
     * @return get the custom serializer id
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
    @SuppressWarnings("unchecked")
    public Map toMap(int serializerId)
    {
        HashMap results = new HashMap();

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
    @SuppressWarnings("unused")
    public Object getAttribute(String attributeName, int serializerId)
    {
        SystemEntity systemEntity = serializers.context.getSystemEntityById(serializerId);

        this.buffer.position(3);

        for (SystemAttribute attribute : systemEntity.getAttributes())
        {
            Object obj;
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

}
