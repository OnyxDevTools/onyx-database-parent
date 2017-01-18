package com.onyx.util;

import java.lang.reflect.Array;
import java.util.Date;

/**
 * Created by tosborn1 on 1/17/17.
 * <p>
 * This class denotes the potential data types used during reflection
 */
enum PropertyType {

    INT(int.class),
    LONG(long.class),
    FLOAT(float.class),
    DOUBLE(double.class),
    BOOLEAN(boolean.class),
    BYTE(byte.class),
    SHORT(short.class),
    CHAR(char.class),
    MUTABLE_INT(Integer.class),
    MUTABLE_LONG(Long.class),
    MUTABLE_FLOAT(Float.class),
    MUTABLE_DOUBLE(Double.class),
    MUTABLE_BOOLEAN(Boolean.class),
    MUTABLE_BYTE(Byte.class),
    MUTABLE_SHORT(Short.class),
    MUTABLE_CHAR(Character.class),
    STRING(String.class),
    DATE(Date.class),
    NULL(null),
    OTHER(null);

    private Class type;

    /**
     * Constructor
     *
     * @param type Class type
     */
    PropertyType(Class type) {
        this.type = type;
    }

    /**
     * Get the enum value with a given class type
     *
     * @param clazz The class property type
     * @return The corresponding enum
     * @since 1.2.0
     */
    public static PropertyType valueOf(Class clazz) {
        if (clazz == null)
            return NULL;
        else if (clazz == int.class)
            return INT;
        else if (clazz == long.class)
            return LONG;
        else if (clazz == float.class)
            return FLOAT;
        else if (clazz == double.class)
            return DOUBLE;
        else if (clazz == boolean.class)
            return BOOLEAN;
        else if (clazz == byte.class)
            return BYTE;
        else if (clazz == short.class)
            return SHORT;
        else if (clazz == char.class)
            return CHAR;
        else if (clazz == Integer.class)
            return MUTABLE_INT;
        else if (clazz == Long.class)
            return MUTABLE_LONG;
        else if (clazz == Float.class)
            return MUTABLE_FLOAT;
        else if (clazz == Double.class)
            return MUTABLE_DOUBLE;
        else if (clazz == Boolean.class)
            return MUTABLE_BOOLEAN;
        else if (clazz == Byte.class)
            return MUTABLE_BYTE;
        else if (clazz == Short.class)
            return MUTABLE_SHORT;
        else if (clazz == Character.class)
            return MUTABLE_CHAR;
        else if (clazz == String.class)
            return STRING;
        else if (clazz == Date.class)
            return DATE;


        return OTHER;
    }

    /**
     * Boolean to indicate whether the data type is a primitive
     *
     * @return Whether the ordered values is within the first 8 indicating it is a primitive
     * @since 1.2.0
     */
    public boolean isPrimitive() {
        return this.ordinal() < 8;
    }

    /**
     * Return whether the object is an array
     * @return The class is an array
     */
    public boolean isArray()
    {
        return this.type != null && this.type.isArray() && this.type.getComponentType().isPrimitive();
    }

    /**
     * Getter for type
     *
     * @return Class type
     */
    public Class getType() {
        return type;
    }
}
