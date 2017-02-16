package com.onyx.buffer;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * This Enum indicates all of the different types of objects that can be serialized
 */
public enum BufferObjectType {

    NULL(null),
    REFERENCE(null),
    ENUM(Enum.class),

    // Primitives
    BYTE(byte.class),
    INT(int.class),
    LONG(long.class),
    SHORT(short.class),
    FLOAT(float.class),
    DOUBLE(double.class),
    BOOLEAN(boolean.class),
    CHAR(char.class),

    // Primitive Arrays
    BYTE_ARRAY(byte[].class),
    INT_ARRAY(int[].class),
    LONG_ARRAY(long[].class),
    SHORT_ARRAY(short[].class),
    FLOAT_ARRAY(float[].class),
    DOUBLE_ARRAY(double[].class),
    BOOLEAN_ARRAY(boolean[].class),
    CHAR_ARRAY(char[].class),
    OBJECT_ARRAY(Object[].class),
    OTHER_ARRAY(Object[].class),

    // Mutable
    MUTABLE_BYTE(Byte.class),
    MUTABLE_INT(Integer.class),
    MUTABLE_LONG(Long.class),
    MUTABLE_SHORT(Short.class),
    MUTABLE_FLOAT(Float.class),
    MUTABLE_DOUBLE(Double.class),
    MUTABLE_BOOLEAN(Boolean.class),
    MUTABLE_CHAR(Character.class),

    // Object Serializable
    BUFFERED(BufferStreamable.class),

    // Objects
    DATE(Date.class),
    STRING(String.class),
    CLASS(Class.class),
    COLLECTION(Collection.class),
    MAP(Map.class),

    OTHER(null);

    private Class type;

    /**
     * Constructor
     * @param type Class type
     */
    BufferObjectType(Class type) {
        this.type = type;
    }

    /**
     * Indicates whether the serializer type is an array
     * @return Whether the enum indicates an array
     */
    public boolean isArray()
    {
        return (this.ordinal() >= BufferObjectType.BYTE_ARRAY.ordinal()
                && this.ordinal() <= BufferObjectType.OBJECT_ARRAY.ordinal());
    }

    /**
     * Get Object type for the class
     *
     * @param object Object in Question
     * @return The serializer type that correlates to that class.
     */
    public static BufferObjectType getTypeCodeForClass(Object object) {

        if(object == null)
            return NULL;

        final Class type = object.getClass();

        if(type.isEnum())
            return ENUM;

        for (BufferObjectType bufferObjectType : BufferObjectType.values()) {
            if (bufferObjectType.type != null && bufferObjectType.type.isAssignableFrom(type)) {
                if(bufferObjectType.equals(BufferObjectType.OBJECT_ARRAY)
                        && type != Object[].class)
                {
                    return OTHER_ARRAY;
                }
                return bufferObjectType;
            }
        }
        return BufferObjectType.OTHER;
    }
}
