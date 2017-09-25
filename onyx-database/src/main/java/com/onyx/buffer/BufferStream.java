package com.onyx.buffer;

import com.onyx.util.map.CompatHashMap;
import com.onyx.util.map.CompatMap;
import com.onyx.exception.BufferingException;
import com.onyx.util.ReflectionField;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Array;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Created by tosborn1 on 7/2/16.
 * <p>
 * The expandableByteBuffer expandableByteBuffer is an object serialization expandableByteBuffer.   It is inspired by the InputStream and and OutputStream
 * and how it interacts with the Externalizable interface.  An exception to that is that the underlying io is to
 * a ByteBuffer.
 */
@SuppressWarnings("unchecked")
public class BufferStream {

    /**
     * Default constructor with no buffer
     */
    @SuppressWarnings("WeakerAccess")
    public BufferStream()
    {
        this(allocate(ExpandableByteBuffer.BUFFER_ALLOCATION));
    }

    /**
     * Constructor with underlying byte buffer
     * @param buffer Byte buffer with contents of the goods
     */
    public BufferStream(ByteBuffer buffer)
    {
        this.expandableByteBuffer = new ExpandableByteBuffer(buffer);
    }

    // Number of references to retain the index number of the said reference
    private int referenceCount = 0;

    // Wrapper for a ByteBuffer to retain reference
    private ExpandableByteBuffer expandableByteBuffer;

    // Indicates whether we are pulling from the expandableByteBuffer or putting into the expandableByteBuffer.
    private boolean isComingFromBuffer = false;

    // Buffer pool to pick from existing so that we can avoid re-allocation
    private final static TreeSet<RecycledBuffer> buffers = new TreeSet<>();

    // References by class and object hash.
    private final CompatMap<Class, CompatMap<Object, Integer>> references = new CompatHashMap<>();

    // References by index number ordered by first used
    private final CompatMap<Integer, Object> referencesByIndex = new CompatHashMap<>();

    // 5 Megabytes of allocated memory max that can be sitting in stale unused buffers waiting to be used
    private static final int MAX_MEMORY_USE = 1024 * 1024 * 5;

    /**
     * Add a reference by class and sequential order by which it was used
     *
     * @param reference Object reference
     */
    private void addReference(Object reference) {
        // If we are pulling from the expandableByteBuffer there is no reason to maintain a hash structure of the object references
        // especially since they are not fully hydrated and may not have valid hashes yet.
        if (isComingFromBuffer) {
            referenceCount++;
            referencesByIndex.put(referenceCount, reference);
        } else {
            references.compute(reference.getClass(), (aClass, objectIntegerMap) -> {
                if (objectIntegerMap == null) {
                    objectIntegerMap = new CompatHashMap<>();
                }

                objectIntegerMap.computeIfAbsent(reference, (integer) -> {
                    referenceCount++;
                    referencesByIndex.put(referenceCount, reference);
                    return referenceCount;
                });

                return objectIntegerMap;
            });
        }
    }

    /**
     * Get the reference index of an object
     * @param reference Reference of an object
     * @return if it exists it will return the index number otherwise -1
     */
    private int referenceIndex(Object reference) {
        if (reference == null)
            return -1;

        Map<Object, Integer> classMap = references.get(reference.getClass());

        if (classMap == null)
            return -1;

        Integer index = classMap.get(reference);
        if (index == null) {
            return -1;
        }
        return index;
    }

    /**
     * Reference of the reference index
     * @param index Index to seek to
     * @return The actual object referenced
     */
    private Object referenceOf(int index) {
        return referencesByIndex.get(index);
    }

    /**
     * Convert an object to the byte buffer representation
     *
     * @param object Object to convert to a byte buffer
     * @return The ByteBuffer the object was serialized into
     * @since 1.1.0
     * @throws BufferingException Generic serialization exception when buffering
     */
    public static ByteBuffer toBuffer(Object object) throws BufferingException {
        ByteBuffer buffer = allocate(ExpandableByteBuffer.BUFFER_ALLOCATION);

        BufferStream bufferStream = new BufferStream();
        bufferStream.expandableByteBuffer = new ExpandableByteBuffer(buffer);
        buffer.putInt(0);

        bufferStream.putObject(object);

        buffer = bufferStream.expandableByteBuffer.buffer;
        buffer.limit(buffer.position());
        buffer.rewind();
        buffer.putInt(buffer.limit());
        buffer.rewind();

        return buffer;
    }

    /**
     * Convert a buffer to an object by de-serializing the bytes in the buffer
     * @param buffer Buffer to read from
     * @return The object read from the buffer
     * @since 1.1.0
     * @throws BufferingException Generic de-serialization exception ocurred when trying to generate
     */
    public static Object fromBuffer(ByteBuffer buffer) throws BufferingException {

        BufferStream bufferStream = new BufferStream();

        int bufferStartingPosition = buffer.position();
        int maxBufferSize = buffer.getInt();

        bufferStream.expandableByteBuffer = new ExpandableByteBuffer(buffer, bufferStartingPosition, maxBufferSize);
        bufferStream.isComingFromBuffer = true;

        Object returnValue;

        try {
            returnValue = bufferStream.getObject();
        } catch (BufferingException e) {
            buffer.position(maxBufferSize + bufferStartingPosition);
            throw e;
        } catch (Exception e) {
            buffer.position(maxBufferSize + bufferStartingPosition);
            if(e instanceof BufferUnderflowException)
                throw new com.onyx.exception.BufferUnderflowException(com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW);
            else if(e instanceof BufferingException)
                throw (BufferingException)e;
            else
                throw new BufferingException(BufferingException.UNKNOWN_DESERIALIZE);
        }

        if (buffer.position() - bufferStartingPosition != maxBufferSize) {
            // Roll the expandableByteBuffer forward so that the next process does not get hung up at the previous position.
            buffer.position(maxBufferSize + bufferStartingPosition);

            // Serialization did not go right, that means we have a serious problem and we do not want it to lead to a corruption
            // therefore we are going to throw an exception
            throw new BufferingException(BufferingException.UNKNOWN_DESERIALIZE);
        }

        return returnValue;
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    //
    // Put Preset Objects
    //
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Put an enum key to the buffer
     *
     * @since 1.1.0
     * @param enumVal enum to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("unused")
    private void putEnum(Enum enumVal) throws BufferingException {
        putObject(enumVal.getClass());
        putByte((byte) enumVal.ordinal());
    }

    /**
     * Put an array of elements.  The elements can be an array of primitives or mutable objects
     *
     * @since 1.1.0
     * @param array Array to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    private void putArray(Object array) throws BufferingException {

        if (array.getClass() == long[].class) {
            final long[] arr = (long[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Long.BYTES * arr.length);
            for (long anArr : arr) expandableByteBuffer.buffer.putLong(anArr);
        } else if (array.getClass() == int[].class) {
            final int[] arr = (int[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Integer.BYTES * arr.length);
            for (int anArr : arr) expandableByteBuffer.buffer.putInt(anArr);
        } else if (array.getClass() == float[].class) {
            final float[] arr = (float[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Float.BYTES * arr.length);
            for (float anArr : arr) expandableByteBuffer.buffer.putFloat(anArr);
        } else if (array.getClass() == byte[].class) {
            final byte[] arr = (byte[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Byte.BYTES * arr.length);
            for (byte anArr : arr) expandableByteBuffer.buffer.put(anArr);
        } else if (array.getClass() == char[].class) {
            final char[] arr = (char[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Character.BYTES * arr.length);
            for (char anArr : arr) expandableByteBuffer.buffer.putChar(anArr);
        } else if (array.getClass() == short[].class) {
            final short[] arr = (short[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Short.BYTES * arr.length);
            for (short anArr : arr) expandableByteBuffer.buffer.putShort(anArr);
        } else if (array.getClass() == boolean[].class) {
            final boolean[] arr = (boolean[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Byte.BYTES * arr.length);
            for (boolean anArr : arr) expandableByteBuffer.buffer.put((byte) ((anArr) ? 1 : 0));
        } else if (array.getClass() == double[].class) {
            final double[] arr = (double[]) array;
            putInt(arr.length);
            expandableByteBuffer.ensureSize(Double.BYTES * arr.length);
            for (double anArr : arr) expandableByteBuffer.buffer.putDouble(anArr);
        } else if(array.getClass() == Object[].class){
            final Object[] arr = (Object[]) array;
            putInt(arr.length);
            for (Object anArr : arr) putObject(anArr);
        } else
        {
            putObjectClass(array.getClass().getComponentType());
            final Object[] arr = (Object[]) array;
            putInt(arr.length);
            for (Object anArr : arr) putObject(anArr);
        }
    }

    /**
     * Put an String to the buffer
     *
     * @since 1.1.0
     * @param value String to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    public void putString(String value) throws BufferingException {
        byte[] stringBytes = value.getBytes();

        putInt(stringBytes.length);
        expandableByteBuffer.ensureSize(stringBytes.length);
        expandableByteBuffer.buffer.put(stringBytes);
    }

    /**
     * Put an Date to the buffer.  This stores as an epoch timestamp
     *
     * @since 1.1.0
     * @param value Date to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    private void putDate(Date value) throws BufferingException {
        putLong(value.getTime());
    }

    /**
     * Put a Class to the buffer
     *
     * @since 1.1.0
     * @param type Class to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    private void putObjectClass(Class type) throws BufferingException {

        addReference(type);

        final String className = type.getName();
        byte[] stringBytes = className.getBytes();

        putInt(stringBytes.length);

        expandableByteBuffer.ensureSize(stringBytes.length);
        expandableByteBuffer.buffer.put(stringBytes);
    }

    /**
     * Put a Collection to a buffer.  If the Collection class is un-accessible it will default to an ArrayCollection
     *
     * @since 1.1.0
     * @param collection Collection to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    public void putCollection(Collection collection) throws BufferingException {

        try {
            Class clazz = Class.forName(collection.getClass().getName());
            putObject(clazz);
        } catch (ClassNotFoundException e) {
            putObject(ArrayList.class);
        }

        putInt(collection.size());

        for (Object aCollection : collection) {
            putObject(aCollection);
        }

    }

    /**
     * Put a Map to the buffer.  If the structure instance class is un-accessible it will chose to use a HashMap
     *
     * @since 1.1.0
     * @param map Map to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("WeakerAccess")
    public void putMap(Map map) throws BufferingException {

        try {
            Class clazz = Class.forName(map.getClass().getName());
            putObject(clazz);
        } catch (ClassNotFoundException e) {
            putObject(HashMap.class);
        }

        putInt(map.size());

        Iterator<Map.Entry> iterator = map.entrySet().iterator();
        Map.Entry entry;

        while (iterator.hasNext()) {
            entry = iterator.next();
            putObject(entry.getKey());
            putObject(entry.getValue());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    //
    // Put Custom Logic Objects
    //
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Put an object that implements BufferStreamable to the buffer.
     *
     * @since 1.1.0
     * @param bufferStreamable BufferStreamable to write to the buffer
     *
     * @throws BufferingException Generic Buffer Exception
     */
    private void putBuffered(BufferStreamable bufferStreamable) throws BufferingException {
        putObject(bufferStreamable.getClass());
        bufferStreamable.write(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    //
    // Put All other types
    //
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * For all mutable objects that are not pre-defined, use this method to put that to the buffer
     *
     * This will iterate through all the fields and put each attribute to the buffer.  If your class
     * requires custom serialization I recommend using BufferStreamable interface.  Also, to ensure
     * the reference is working as it should please implement the hashCode and equals methods to make
     * sure we can identify the object and is unique.
     *
     * @since 1.1.0
     * @param object Generic mutable object to write to the buffer
     *
     * @throws BufferingException Generic Buffer Exception
     */
    private void putOther(Object object) throws BufferingException {

        putObject(object.getClass());
        addReference(object);

        // Iterate through the fields and put them on the expandableByteBuffer
        for (ReflectionField field : ReflectionUtil.INSTANCE.getFields(object)) {
            try {
                if (field.getType() == int.class)
                    putInt(ReflectionUtil.INSTANCE.getInt(object, field));
                else if (field.getType() == long.class)
                    putLong(ReflectionUtil.INSTANCE.getLong(object, field));
                else if (field.getType() == byte.class)
                    putByte(ReflectionUtil.INSTANCE.getByte(object, field));
                else if (field.getType() == float.class)
                    putFloat(ReflectionUtil.INSTANCE.getFloat(object, field));
                else if (field.getType() == double.class)
                    putDouble(ReflectionUtil.INSTANCE.getDouble(object, field));
                else if (field.getType() == boolean.class)
                    putBoolean(ReflectionUtil.INSTANCE.getBoolean(object, field));
                else if (field.getType() == short.class)
                    putShort(ReflectionUtil.INSTANCE.getShort(object, field));
                else if (field.getType() == char.class)
                    putChar(ReflectionUtil.INSTANCE.getChar(object, field));
                else {
                    final Object attributeObject = ReflectionUtil.INSTANCE.getObject(object, field);
                    putObject(attributeObject);
                }
            } catch (IllegalAccessException e) {
                throw new BufferingException(BufferingException.ILLEGAL_ACCESS_EXCEPTION + field.getName());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    //
    // Put Primitives
    //
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Put byte to the buffer
     *
     * @since 1.1.0
     * @param value byte to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public void putByte(byte value) throws BufferingException {
        expandableByteBuffer.ensureSize(Byte.BYTES);
        expandableByteBuffer.buffer.put(value);
    }

    /**
     * Put an int to the buffer
     *
     * @since 1.1.0
     * @param value int to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public void putInt(int value) throws BufferingException {
        expandableByteBuffer.ensureSize(Integer.BYTES);
        expandableByteBuffer.buffer.putInt(value);
    }

    /**
     * Put long to the buffer
     *
     * @since 1.1.0
     * @param value long to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public void putLong(long value) throws BufferingException {
        expandableByteBuffer.ensureSize(Long.BYTES);
        expandableByteBuffer.buffer.putLong(value);
    }

    /**
     * Put short to the buffer
     *
     * @since 1.1.0
     * @param value short to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public void putShort(short value) throws BufferingException {
        expandableByteBuffer.ensureSize(Short.BYTES);
        expandableByteBuffer.buffer.putShort(value);
    }

    /**
     * Put float to the buffer
     *
     * @since 1.1.0
     * @param value float to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    private void putFloat(float value) throws BufferingException {
        expandableByteBuffer.ensureSize(Float.BYTES);
        expandableByteBuffer.buffer.putFloat(value);
    }

    /**
     * Put double to the buffer
     *
     * @since 1.1.0
     * @param value double to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    private void putDouble(double value) throws BufferingException {
        expandableByteBuffer.ensureSize(Double.BYTES);
        expandableByteBuffer.buffer.putDouble(value);
    }

    /**
     * Put boolean to the buffer
     *
     * @since 1.1.0
     * @param value boolean to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public void putBoolean(boolean value) throws BufferingException {
        expandableByteBuffer.ensureSize(Byte.BYTES);
        expandableByteBuffer.buffer.put((value) ? (byte) 1 : (byte) 0);
    }

    /**
     * Put char to the buffer
     *
     * @since 1.1.0
     * @param value byte to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    private void putChar(char value) throws BufferingException {
        expandableByteBuffer.ensureSize(Character.BYTES);
        expandableByteBuffer.buffer.putChar(value);
    }

    /**
     * Put a generic object to the buffer.  Note this is less efficient because it has to abstract the type and add that to
     * the packet.  For primitives, I recommend using the primitive put methods.
     *
     * @since 1.1.0
     * @param object object to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    public void putObject(Object object) throws BufferingException {

        BufferObjectType bufferObjectType = BufferObjectType.getTypeCodeForClass(object);
        short referenceNumber = (short) referenceIndex(object);
        if (referenceNumber > -1) bufferObjectType = BufferObjectType.REFERENCE;

        try {

            // Put the serializer type
            putByte((byte) bufferObjectType.ordinal());

            switch (bufferObjectType) {
                case NULL:
                    return;
                case REFERENCE:
                    putShort(referenceNumber);
                    break;
                case ENUM:
                    putEnum((Enum) object);
                    break;
                case BYTE:
                case MUTABLE_BYTE:
                    putByte((byte) object);
                    break;
                case INT:
                case MUTABLE_INT:
                    putInt((int) object);
                    break;
                case LONG:
                case MUTABLE_LONG:
                    putLong((long) object);
                    break;
                case SHORT:
                case MUTABLE_SHORT:
                    putShort((short) object);
                    break;
                case FLOAT:
                case MUTABLE_FLOAT:
                    putFloat((float) object);
                    break;
                case DOUBLE:
                case MUTABLE_DOUBLE:
                    putDouble((double) object);
                    break;
                case BOOLEAN:
                case MUTABLE_BOOLEAN:
                    putBoolean((boolean) object);
                    break;
                case CHAR:
                case MUTABLE_CHAR:
                    putChar((char) object);
                    break;
                case BYTE_ARRAY:
                case INT_ARRAY:
                case LONG_ARRAY:
                case SHORT_ARRAY:
                case FLOAT_ARRAY:
                case DOUBLE_ARRAY:
                case BOOLEAN_ARRAY:
                case CHAR_ARRAY:
                case OBJECT_ARRAY:
                case OTHER_ARRAY:
                    putArray(object);
                    break;
                case BUFFERED:
                    putBuffered((BufferStreamable) object);
                    break;
                case DATE:
                    putDate((Date) object);
                    break;
                case STRING:
                    putString((String) object);
                    break;
                case CLASS:
                    putObjectClass((Class) object);
                    break;
                case COLLECTION:
                    putCollection((Collection) object);
                    break;
                case MAP:
                    putMap((Map) object);
                    break;
                case OTHER:
                    putOther(object);
                    break;
            }
        } catch (Exception e) {
            if(e instanceof BufferUnderflowException)
                throw new com.onyx.exception.BufferUnderflowException(com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW, (object != null) ? object.getClass() : null);
            else if(e instanceof BufferingException)
                throw (BufferingException)e;
            else
                throw new BufferingException(BufferingException.UNKNOWN_DESERIALIZE, (object != null) ? object.getClass() : null);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    //
    // Reflection Helpers
    //
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Instantiate an object
     *
     * @param type The type of class to instantiate
     * @return The instantiated object.
     * @throws BufferingException Instantiation failure
     */
    @SuppressWarnings("WeakerAccess")
    public Object instantiate(Class type) throws BufferingException {
        try {
            return ReflectionUtil.INSTANCE.instantiate(type);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new BufferingException(BufferingException.CANNOT_INSTANTIATE, type);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Get Primitives
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get Long from the buffer
     *
     * @since  1.1.0
     * @return long read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public long getLong() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Long.BYTES);
        return expandableByteBuffer.buffer.getLong();
    }

    /**
     * Get int from the buffer
     *
     * @since  1.1.0
     * @return int read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public int getInt() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
        return expandableByteBuffer.buffer.getInt();
    }

    /**
     * Get float from the buffer
     *
     * @since  1.1.0
     * @return float read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public float getFloat() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Float.BYTES);
        return expandableByteBuffer.buffer.getFloat();
    }

    /**
     * Get double from the buffer
     *
     * @since  1.1.0
     * @return double read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public double getDouble() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Double.BYTES);
        return expandableByteBuffer.buffer.getDouble();
    }

    /**
     * Get byte from the buffer
     *
     * @since  1.1.0
     * @return byte read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public byte getByte() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Byte.BYTES);
        return expandableByteBuffer.buffer.get();
    }

    /**
     * Get short from the buffer
     *
     * @since  1.1.0
     * @return short read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public short getShort() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Short.BYTES);
        return expandableByteBuffer.buffer.getShort();
    }

    /**
     * Get boolean from the buffer
     *
     * @since  1.1.0
     * @return boolean read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public boolean getBoolean() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Byte.BYTES);
        return (expandableByteBuffer.buffer.get() == 1);
    }

    /**
     * Get char from the buffer
     *
     * @since  1.1.0
     * @return char read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public char getChar() throws BufferingException {
        expandableByteBuffer.ensureRequiredSize(Character.BYTES);
        return expandableByteBuffer.buffer.getChar();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Get Object Methods
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get a generic object.  Note: This must have been wrapped to ensure the type was added to the buffer so we know what we are
     * getting.  This will read the object detect if it is null, a reference, and gather the type to read it into.
     *
     * @since  1.1.0
     * @return Object read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    public Object getObject() throws BufferingException {

        expandableByteBuffer.ensureRequiredSize(Byte.BYTES);
        BufferObjectType bufferObjectType = BufferObjectType.values()[expandableByteBuffer.buffer.get()];

        switch (bufferObjectType) {
            case NULL:
                return null;
            case REFERENCE:
                return referenceOf(getShort());
            case ENUM:
                return getEnum();
            case BYTE:
            case MUTABLE_BYTE:
                return getByte();
            case INT:
            case MUTABLE_INT:
                return getInt();
            case LONG:
            case MUTABLE_LONG:
                return getLong();
            case SHORT:
            case MUTABLE_SHORT:
                return getShort();
            case FLOAT:
            case MUTABLE_FLOAT:
                return getFloat();
            case DOUBLE:
            case MUTABLE_DOUBLE:
                return getDouble();
            case BOOLEAN:
            case MUTABLE_BOOLEAN:
                return getBoolean();
            case CHAR:
            case MUTABLE_CHAR:
                return getChar();
            case BYTE_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case SHORT_ARRAY:
            case FLOAT_ARRAY:
            case DOUBLE_ARRAY:
            case BOOLEAN_ARRAY:
            case CHAR_ARRAY:
            case OBJECT_ARRAY:
            case OTHER_ARRAY:
                return getArray(bufferObjectType);
            case BUFFERED:
                return getBuffered();
            case DATE:
                return getDate();
            case STRING:
                return getString();
            case CLASS:
                return getObjectClass();
            case COLLECTION:
                return getCollection();
            case MAP:
                return getMap();
            case OTHER:
                return getOther();
        }

        return null;
    }

    /**
     * Get object from the buffer that is not a pre defined object.  This will iterate through the fields and
     * de-seserialize them individually based on their type.
     *
     * @return The Object read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("WeakerAccess")
    public Object getOther() throws BufferingException {
        Class objectType = (Class) getObject();
        Object instance;
        try {
            instance = instantiate(objectType);
            addReference(instance);

            for (ReflectionField reflectionField : ReflectionUtil.INSTANCE.getFields(instance)) {

                if (reflectionField.getType() == long.class)
                    ReflectionUtil.INSTANCE.setLong(instance, reflectionField, getLong());
                else if (reflectionField.getType() == int.class)
                    ReflectionUtil.INSTANCE.setInt(instance, reflectionField, getInt());
                else if (reflectionField.getType() == double.class)
                    ReflectionUtil.INSTANCE.setDouble(instance, reflectionField, getDouble());
                else if (reflectionField.getType() == float.class)
                    ReflectionUtil.INSTANCE.setFloat(instance, reflectionField, getFloat());
                else if (reflectionField.getType() == byte.class)
                    ReflectionUtil.INSTANCE.setByte(instance, reflectionField, getByte());
                else if (reflectionField.getType() == char.class)
                    ReflectionUtil.INSTANCE.setChar(instance, reflectionField, getChar());
                else if (reflectionField.getType() == short.class)
                    ReflectionUtil.INSTANCE.setShort(instance, reflectionField, getShort());
                else if (reflectionField.getType() == boolean.class)
                    ReflectionUtil.INSTANCE.setBoolean(instance, reflectionField, getBoolean());
                else
                    ReflectionUtil.INSTANCE.setObject(instance, reflectionField, getObject());
            }
        }
        catch (Exception e)
        {
            if(e instanceof BufferUnderflowException)
                throw new com.onyx.exception.BufferUnderflowException(com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW, objectType);
            else if(e instanceof BufferingException)
                throw (BufferingException)e;
            else
                throw new BufferingException(BufferingException.UNKNOWN_DESERIALIZE, objectType);
        }

        return instance;
    }

    /**
     * Get object class type from the buffer
     *
     * @since  1.1.0
     * @return class type read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    private Class getObjectClass() throws BufferingException {

        final int stringSize = expandableByteBuffer.buffer.getInt();
        byte[] stringBytes = new byte[stringSize];
        expandableByteBuffer.buffer.get(stringBytes);
        final String className = new String(stringBytes);

        try {
            Class returnValue = Class.forName(className);
            addReference(returnValue);
            return returnValue;
        } catch (ClassNotFoundException e) {
            throw new BufferingException(BufferingException.UNKNOWN_CLASS + className);
        }
    }

    /**
     * Get string from the buffer
     *
     * @since  1.1.0
     * @return string read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("RedundantThrows")
    public String getString() throws BufferingException {
        final int stringSize = expandableByteBuffer.buffer.getInt();
        byte[] stringBytes = new byte[stringSize];
        expandableByteBuffer.buffer.get(stringBytes);
        return new String(stringBytes);
    }

    /**
     * Get Date from the buffer.  This uses the epoch timestamp
     *
     * @since  1.1.0
     * @return Date read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public Date getDate() throws BufferingException {
        final long epoch = expandableByteBuffer.buffer.getLong();
        return new Date(epoch);
    }

    /**
     * Get Collection from the buffer.  If there is an exception during instantiation, this will fail and
     * cause the entire de-serialization to fail.  You must add a public Collection class to the buffer rather
     * than a static class.  Also, it must exist on both the reader and writer jvm.
     *
     * @since  1.1.0
     * @return Collection read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    public Collection getCollection() throws BufferingException {

        Class collectionClass = (Class) getObject();
        int size = expandableByteBuffer.buffer.getInt();

        Collection collection;
        try {
            collection = (Collection) collectionClass.newInstance();
        } catch (InstantiationException e) {
            collection = new ArrayList();
        } catch (IllegalAccessException e) {
            throw new BufferingException(BufferingException.CANNOT_INSTANTIATE, collectionClass);
        }

        for (int i = 0; i < size; i++) {
            collection.add(getObject());
        }

        return collection;
    }

    /**
     * Get Map from the buffer.  If there is an exception during instantiation, this will fail and
     * cause the entire de-serialization to fail.  You must add a public Map class to the buffer rather
     * than a static class.  Also, it must exist on both the reader and writer jvm.
     *
     * @since  1.1.0
     * @return Map read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("WeakerAccess")
    public Map getMap() throws BufferingException {
        Class mapClass = (Class) getObject();
        Map map;
        try {
            map = (Map) mapClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass);
        }

        int mapSize = expandableByteBuffer.buffer.getInt();

        for (int i = 0; i < mapSize; i++) {
            map.put(getObject(), getObject());
        }
        return map;
    }

    /**
     * Get Array of primitives or objects
     *
     * @since 1.1.0
     * @param type The serializer type that specifies which type of array to de-serialize
     * @return An Array
     * @throws BufferingException Generic Buffer Exception
     */
    private Object getArray(BufferObjectType type) throws BufferingException {
        if (type == BufferObjectType.LONG_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final long[] arr = new long[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Long.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.getLong();
            return arr;
        } else if (type == BufferObjectType.INT_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final int[] arr = new int[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.getInt();
            return arr;
        } else if (type == BufferObjectType.FLOAT_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final float[] arr = new float[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Float.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.getFloat();
            return arr;
        } else if (type == BufferObjectType.BYTE_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final byte[] arr = new byte[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Byte.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.get();
            return arr;
        } else if (type == BufferObjectType.CHAR_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final char[] arr = new char[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Character.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.getChar();
            return arr;
        } else if (type == BufferObjectType.SHORT_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final short[] arr = new short[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Short.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.getShort();
            return arr;
        } else if (type == BufferObjectType.BOOLEAN_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final boolean[] arr = new boolean[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Byte.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.get() == 1;
            return arr;
        } else if (type == BufferObjectType.DOUBLE_ARRAY) {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final double[] arr = new double[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(Double.BYTES * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = expandableByteBuffer.buffer.getDouble();
            return arr;
        }
        else if (type == BufferObjectType.OTHER_ARRAY) {
            Object arr = Array.newInstance(getObjectClass(), getInt());
            for (int i = 0; i < Array.getLength(arr); i++) {
                Object value = getObject();
                Array.set(arr, i, value);
            }
            return arr;
        } else {
            expandableByteBuffer.ensureRequiredSize(Integer.BYTES);
            final Object[] arr = new Object[expandableByteBuffer.buffer.getInt()];
            expandableByteBuffer.ensureRequiredSize(3 * arr.length);
            for (int i = 0; i < arr.length; i++)
                arr[i] = getObject();
            return arr;
        }
    }

    /**
     * Get an enum from the buffer
     *
     * @since 1.1.0
     * @return The enum from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @SuppressWarnings("WeakerAccess")
    public Enum getEnum() throws BufferingException {
        Class enumClass = (Class) getObject();
        expandableByteBuffer.ensureRequiredSize(Byte.BYTES);

        return (Enum) enumClass.getEnumConstants()[getByte()];
    }

    /**
     * Get Buffer Streamable object that implements BufferStreamable interface.
     * First pulls the type of class, instantiates and invokes the read method.
     *
     * @since 1.1.0
     * @return An instantiated BufferStreamable object
     * @throws BufferingException Generic Buffer Exception
     */
    private BufferStreamable getBuffered() throws BufferingException {
        final BufferStreamable bufferStreamable;
        Class classToInstantiate = (Class) getObject();
        bufferStreamable = (BufferStreamable) instantiate(classToInstantiate);
        bufferStreamable.read(this);
        return bufferStreamable;
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer
     */
    public static ByteBuffer allocate(int count) {
        synchronized (buffers) {
            if (buffers.size() > 0) {
                RecycledBuffer reclaimedBuffer = buffers.higher(new RecycledBuffer(count));
                if (reclaimedBuffer != null) {
                    staleBufferMemory -= reclaimedBuffer.getBuffer().capacity();
                    buffers.remove(reclaimedBuffer);
                    return reclaimedBuffer.getBuffer();
                }
            }
            final ByteBuffer buffer = ByteBuffer.allocate(count);
            buffer.order(ByteOrder.BIG_ENDIAN);
            return buffer;
        }
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer and limit to the amount of bytes
     */
    public static ByteBuffer allocateAndLimit(int count) {
        ByteBuffer buffer = allocate(count);
        buffer.limit(count);
        return buffer;
    }

    private static volatile int staleBufferMemory = 0;

    /**
     * Recycle a byte buffer to be reused
     * @param buffer byte buffer to recycle and reuse
     */
    public static void recycle(ByteBuffer buffer) {
        synchronized (buffers) {
            buffer.limit(buffer.capacity());
            buffers.add(new RecycledBuffer(buffer));
            staleBufferMemory += buffer.capacity();

            // Remove the upper and lower bounds to clean up memory
            while(staleBufferMemory > MAX_MEMORY_USE
                    && buffers.size() > 0)
            {
                if(buffers.size() > 0)
                {
                    RecycledBuffer recycledBuffer = buffers.pollLast();
                    buffers.remove(recycledBuffer);
                    staleBufferMemory-=recycledBuffer.capacity();
                }
                if(buffers.size() > 0) {
                    RecycledBuffer recycledBuffer = buffers.pollFirst();
                    buffers.remove(recycledBuffer);
                    staleBufferMemory -= recycledBuffer.capacity();
                }
            }
        }
    }

    /**
     * Getter for underlying byte buffer
     * @return The underlying byte buffer with the goods
     */
    @SuppressWarnings("unused")
    public ByteBuffer getByteBuffer()
    {
        return expandableByteBuffer.buffer;
    }

    /**
     * Flip the underlying byte buffer
     */
    @SuppressWarnings("unused")
    public void flip()
    {
        this.expandableByteBuffer.buffer.flip();
    }
}
