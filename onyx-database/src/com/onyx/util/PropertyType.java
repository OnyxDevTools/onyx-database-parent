package com.onyx.util;

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

    final private Class type;

    private static final Class INT_CLASS = int.class;
    private static final Class DOUBLE_CLASS = double.class;
    private static final Class LONG_CLASS = long.class;
    private static final Class BOOLEAN_CLASS = boolean.class;
    private static final Class FLOAT_CLASS = float.class;
    private static final Class BYTE_CLASS = byte.class;
    private static final Class SHORT_CLASS = short.class;
    private static final Class CHAR_CLASS = char.class;

    private static final Class MUTABLE_INT_CLASS = Integer.class;
    private static final Class MUTABLE_DOUBLE_CLASS = Double.class;
    private static final Class MUTABLE_LONG_CLASS = Long.class;
    private static final Class MUTABLE_BOOLEAN_CLASS = Boolean.class;
    private static final Class MUTABLE_FLOAT_CLASS = Float.class;
    private static final Class MUTABLE_BYTE_CLASS = Byte.class;
    private static final Class MUTABLE_SHORT_CLASS = Short.class;
    private static final Class MUTABLE_CHAR_CLASS = Character.class;
    private static final Class STRING_CLASS = String.class;
    private static final Class DATE_CLASS = Date.class;

    /**
     * Constructor
     *
     * @param type Class type
     */
    @SuppressWarnings("unused")
    PropertyType(Class type) {
        this.type = type;
    }

    /**
     * This method is used to gather the primitive property type only.  If it is not primitive, OTHER is returned.
     * THe reason why it exists is to be more efficient that valueOf(
     *
     * @param clazz The class property type
     * @return The corresponding enum
     * @since 1.2.0
     */
    public static PropertyType primitiveValueOf(final Class clazz) {
        if (clazz == INT_CLASS)
            return INT;
        else if (clazz == LONG_CLASS)
            return LONG;
        else if (clazz == DOUBLE_CLASS)
            return DOUBLE;
        else if (clazz == BOOLEAN_CLASS)
            return BOOLEAN;
        else if (clazz == FLOAT_CLASS)
            return FLOAT;
        else if (clazz == BYTE_CLASS)
            return BYTE;
        else if (clazz == SHORT_CLASS)
            return SHORT;
        else if (clazz == CHAR_CLASS)
            return CHAR;

        return OTHER;
    }

    /**
     * Get the enum value with a given class type
     *
     * @param clazz The class property type
     * @return The corresponding enum
     * @since 1.2.0
     */
    public static PropertyType valueOf(final Class clazz) {
        if (clazz == null)
            return NULL;
        else if (clazz == INT_CLASS)
            return INT;
        else if (clazz == LONG_CLASS)
            return LONG;
        else if (clazz == FLOAT_CLASS)
            return FLOAT;
        else if (clazz == DOUBLE_CLASS)
            return DOUBLE;
        else if (clazz == BOOLEAN_CLASS)
            return BOOLEAN;
        else if (clazz == BYTE_CLASS)
            return BYTE;
        else if (clazz == SHORT_CLASS)
            return SHORT;
        else if (clazz == CHAR_CLASS)
            return CHAR;
        else if (clazz == MUTABLE_INT_CLASS)
            return MUTABLE_INT;
        else if (clazz == MUTABLE_LONG_CLASS)
            return MUTABLE_LONG;
        else if (clazz == MUTABLE_FLOAT_CLASS)
            return MUTABLE_FLOAT;
        else if (clazz == MUTABLE_DOUBLE_CLASS)
            return MUTABLE_DOUBLE;
        else if (clazz == MUTABLE_BOOLEAN_CLASS)
            return MUTABLE_BOOLEAN;
        else if (clazz == MUTABLE_BYTE_CLASS)
            return MUTABLE_BYTE;
        else if (clazz == MUTABLE_SHORT_CLASS)
            return MUTABLE_SHORT;
        else if (clazz == MUTABLE_CHAR_CLASS)
            return MUTABLE_CHAR;
        else if (clazz == STRING_CLASS)
            return STRING;
        else if (clazz == DATE_CLASS)
            return DATE;


        return OTHER;
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
    @SuppressWarnings("unused")
    public Class getType() {
        return type;
    }
}
