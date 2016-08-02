package com.onyx.util;

import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Relationship;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created by tosborn1 on 8/2/16.
 *
 * The purpose of this class is to encapsulate how we are performing reflection.  The default way is to use the unsafe api
 * but if it is not available, we default to generic reflection.
 */
public class ReflectionUtil
{

    private static sun.misc.Unsafe theUnsafe = null;

    // Cache of class' fields
    protected static final Map<Class, List<OffsetField>> classFields = Collections.synchronizedMap(new WeakHashMap());

    // Get Unsafe instance
    static {
        try {
            final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            theUnsafe = (sun.misc.Unsafe) field.get(null);
        } catch (Exception e) {
        }
    }

    /**
     * Get all the fields to serialize
     *
     * @param object The object to read the fields from
     * @return Fields with their offsets
     * @throws Exception Generic exception due to the shittyness of reflection
     */
    public static List<OffsetField> getFields(Object object)
    {
        final Class clazz = object.getClass();
        final boolean isManagedEntity = object.getClass().isAnnotationPresent(Entity.class);

        return classFields.compute(clazz, (aClass, fields) -> {
            if (fields == null) {
                fields = new ArrayList<>();

                while (aClass != Object.class
                        && aClass != Exception.class
                        && aClass != Throwable.class) {
                    for (Field f : aClass.getDeclaredFields()) {
                        if ((f.getModifiers() & Modifier.STATIC) == 0
                                && !Modifier.isTransient(f.getModifiers())
                                && f.getType() != Exception.class
                                && f.getType() != Throwable.class) {
                            if (!isManagedEntity) {
                                if (theUnsafe != null) {
                                    fields.add(new OffsetField(theUnsafe.objectFieldOffset(f), f.getName(), f));
                                } else {
                                    fields.add(new OffsetField(-1, f.getName(), f));
                                }
                            }
                            else if (f.isAnnotationPresent(Attribute.class)
                                    || f.isAnnotationPresent(Relationship.class))
                            {
                                if (theUnsafe != null) {
                                    fields.add(new OffsetField(theUnsafe.objectFieldOffset(f), f.getName(), f));
                                } else {
                                    fields.add(new OffsetField(-1, f.getName(), f));
                                }
                            }
                        }
                    }
                    aClass = aClass.getSuperclass();
                }

                fields.sort((o1, o2) -> o1.name.compareTo(o2.name));
            }
            return fields;
        });
    }

    /**
     * Instantiate an instance with a class type
     * Note: If using the unsafe API, this will bypass the constructor
     *
     * @param type the type of class to instantiate
     *
     * @return the fully instantiated object
     *
     * @throws InstantiationException Exception thrown when using unsafe to allocate an instance
     * @throws IllegalAccessException Exception thrown when using regular reflection
     */
    public static Object instantiate(Class type) throws InstantiationException, IllegalAccessException {
        if(theUnsafe != null)
            return theUnsafe.allocateInstance(type);

        return type.newInstance();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Get Methods
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get Int for an object and a field
     *
     * @param parent The object to get the int field from
     * @param offsetField The field to reflect on
     * @return a primitive int
     */
    public static int getInt(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getInt(parent, offsetField.offset);

        return (int)offsetField.field.getInt(parent);
    }

    /**
     * Get byte for an object and a field
     *
     * @param parent The object to get the byte field from
     * @param offsetField The field to reflect on
     * @return a primitive byte
     */
    public static byte getByte(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getByte(parent, offsetField.offset);

        return (byte)offsetField.field.getByte(parent);
    }

    /**
     * Get long for an object and a field
     *
     * @param parent The object to get the long field from
     * @param offsetField The field to reflect on
     * @return a primitive long
     */
    public static long getLong(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getLong(parent, offsetField.offset);

        return (long)offsetField.field.getLong(parent);
    }

    /**
     * Get float for an object and a field
     *
     * @param parent The object to get the float field from
     * @param offsetField The field to reflect on
     * @return a primitive float
     */
    public static float getFloat(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getFloat(parent, offsetField.offset);

        return (float)offsetField.field.getFloat(parent);
    }

    /**
     * Get double for an object and a field
     *
     * @param parent The object to get the double field from
     * @param offsetField The field to reflect on
     * @return a primitive double
     */
    public static double getDouble(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getDouble(parent, offsetField.offset);

        return (double)offsetField.field.getDouble(parent);
    }

    /**
     * Get short for an object and a field
     *
     * @param parent The object to get the boolean field from
     * @param offsetField The field to reflect on
     * @return a primitive boolean
     */
    public static boolean getBoolean(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getBoolean(parent, offsetField.offset);

        return (boolean)offsetField.field.getBoolean(parent);
    }

    /**
     * Get short for an object and a field
     *
     * @param parent The object to get the short field from
     * @param offsetField The field to reflect on
     * @return a primitive short
     */
    public static short getShort(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getShort(parent, offsetField.offset);

        return (short)offsetField.field.getShort(parent);
    }

    /**
     * Get char for an object and a field
     *
     * @param parent The object to get the char field from
     * @param offsetField The field to reflect on
     * @return a primitive char
     */
    public static char getChar(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getChar(parent, offsetField.offset);

        return (char)offsetField.field.getChar(parent);
    }

    /**
     * Get object for an object and a field
     *
     * @param parent The object to get the object field from
     * @param offsetField The field to reflect on
     * @return a mutable object of any kind
     */
    public static Object getObject(Object parent, OffsetField offsetField) throws IllegalAccessException
    {
        if(theUnsafe != null)
            return theUnsafe.getObject(parent, offsetField.offset);

        return offsetField.field.get(parent);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Put Methods
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Put an int on an parent object
     *
     * @param parent The object to put the int on
     * @param offsetField The field to reflect on
     * @param value int value to set
     */
    public static void setInt(Object parent, OffsetField offsetField, int value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putInt(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setInt(parent, value);
    }

    /**
     * Put an long on an parent object
     *
     * @param parent The object to put the long on
     * @param offsetField The field to reflect on
     * @param value long value to set
     */
    public static void setLong(Object parent, OffsetField offsetField, long value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putLong(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setLong(parent, value);
    }

    /**
     * Put an byte on an parent object
     *
     * @param parent The object to put the byte on
     * @param offsetField The field to reflect on
     * @param value byte value to set
     */
    public static void setByte(Object parent, OffsetField offsetField, byte value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putByte(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setByte(parent, value);
    }

    /**
     * Put an float on an parent object
     *
     * @param parent The object to put the float on
     * @param offsetField The field to reflect on
     * @param value float value to set
     */
    public static void setFloat(Object parent, OffsetField offsetField, float value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putFloat(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setFloat(parent, value);
    }

    /**
     * Put an double on an parent object
     *
     * @param parent The object to put the double on
     * @param offsetField The field to reflect on
     * @param value double value to set
     */
    public static void setDouble(Object parent, OffsetField offsetField, double value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putDouble(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setDouble(parent, value);
    }

    /**
     * Put an short on an parent object
     *
     * @param parent The object to put the short on
     * @param offsetField The field to reflect on
     * @param value short value to set
     */
    public static void setShort(Object parent, OffsetField offsetField, short value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putShort(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setShort(parent, value);
    }

    /**
     * Put an boolean on an parent object
     *
     * @param parent The object to put the boolean on
     * @param offsetField The field to reflect on
     * @param value boolean value to set
     */
    public static void setBoolean(Object parent, OffsetField offsetField, boolean value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putBoolean(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setBoolean(parent, value);
    }

    /**
     * Put an char on an parent object
     *
     * @param parent The object to put the char on
     * @param offsetField The field to reflect on
     * @param value char value to set
     */
    public static void setChar(Object parent, OffsetField offsetField, char value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putChar(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.setChar(parent, value);
    }

    /**
     * Put an object on an parent object
     *
     * @param parent The object to put the object on
     * @param offsetField The field to reflect on
     * @param value object value to set
     */
    public static void setObject(Object parent, OffsetField offsetField, Object value) throws IllegalAccessException
    {
        if(theUnsafe != null) {
            theUnsafe.putObject(parent, offsetField.offset, value);
            return;
        }

        offsetField.field.set(parent, value);
    }
}
