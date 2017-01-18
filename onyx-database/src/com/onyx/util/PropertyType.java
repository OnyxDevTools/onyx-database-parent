package com.onyx.util;

import java.util.Date;

/**
 * Created by tosborn1 on 1/17/17.
 *
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
     * @param type Class type
     */
    PropertyType(Class type)
    {
        this.type = type;
    }

    /**
     * Get the enum value with a given class type
     * @param clazz The class property type
     * @return The corresponding enum
     *
     * @since 1.2.0
     */
    public static PropertyType valueOf(Class clazz)
    {
        if(clazz == null)
            return NULL;

        PropertyType compare;

        for(int i = 0; i < values().length; i++) {
            compare = values()[i];
            if (clazz == compare.type) return compare;
        }

        return OTHER;
    }

    /**
     * Boolean to indicate whether the data type is a primitive
     * @return Whether the ordered values is within the first 8 indicating it is a primitive
     * @since 1.2.0
     */
    public boolean isPrimitive()
    {
        return this.ordinal() < 8;
    }
}
