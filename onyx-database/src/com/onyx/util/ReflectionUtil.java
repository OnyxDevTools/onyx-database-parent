package com.onyx.util;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.persistence.IManagedEntity;
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
     * Helper for getting field that may be an inherited field
     *
     * @param clazz Parent class to reflect
     * @param attribute Attribute Name
     * @return Offset field which is a wrapper so that it can use Unsafe for reflection if it is available
     * @throws AttributeMissingException The attribute does not exist
     */
    public static OffsetField getOffsetField(Class clazz, String attribute) throws AttributeMissingException
    {
        Field field = getField(clazz, attribute);
        return getOffsetField(field);
    }

    /**
     * Helper for getting the offset field which is a wrapper so that it can be used by the Unsafe for reflection
     *
     * @param field Reflect field to wrap
     * @return The offset field
     */
    public static OffsetField getOffsetField(Field field)
    {
        if (theUnsafe != null)
            return new OffsetField(theUnsafe.objectFieldOffset(field), field.getName(), field);

        return new OffsetField(-1, field.getName(), field);
    }

    /**
     * Helper for getting field that may be an inherited field
     *
     * @param clazz Parent class to reflect upon
     * @param attribute Attribute to get field of
     * @return The Field that corresponds to that attribute name
     * @throws AttributeMissingException Exception thrown when the field is not there
     */
    public static Field getField(Class clazz, String attribute) throws AttributeMissingException
    {
        while(clazz != Object.class)
        {
            try
            {
                Field f = clazz.getDeclaredField(attribute);
                if (f != null)
                {
                    if(!f.isAccessible())
                    {
                        f.setAccessible(true);
                    }
                    return f;
                }
            } catch (NoSuchFieldException e)
            {
                clazz = clazz.getSuperclass();
            }
        }

        throw new AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE);
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

    /**
     * This method is to return any value from a field using reflection.  It
     * can either return a primitive or an object.  Note: If inteneded to get a
     * primitive, I recommend using the other api methods to avoid autoboxing.
     *
     * @param object Parent object
     * @param offsetField field to get
     * @return field value
     */
    public static Object getAny(Object object, OffsetField offsetField) throws AttributeTypeMismatchException
    {
        try
        {
            if (offsetField.type == int.class)
                return ReflectionUtil.getInt(object, offsetField);
            else if (offsetField.type == long.class)
                return ReflectionUtil.getLong(object, offsetField);
            else if (offsetField.type == byte.class)
                return ReflectionUtil.getByte(object, offsetField);
            else if (offsetField.type == float.class)
                return ReflectionUtil.getFloat(object, offsetField);
            else if (offsetField.type == double.class)
                return ReflectionUtil.getDouble(object, offsetField);
            else if (offsetField.type == boolean.class)
                return ReflectionUtil.getBoolean(object, offsetField);
            else if (offsetField.type == short.class)
                return ReflectionUtil.getShort(object, offsetField);
            else if (offsetField.type == char.class)
                return ReflectionUtil.getChar(object, offsetField);
            else if (!offsetField.type.isPrimitive())
                return ReflectionUtil.getObject(object, offsetField);
            else
                throw new AttributeTypeMismatchException(AttributeTypeMismatchException.UNKNOWN_EXCEPTION, offsetField.type, object.getClass(), offsetField.name);
        }catch (IllegalAccessException e)
        {
            return null;
        }
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
    public static void setObject(Object parent, OffsetField offsetField, Object value) throws IllegalAccessException,AttributeTypeMismatchException
    {
        if(theUnsafe != null) {
            if(value == null || offsetField.field.getType().isAssignableFrom(value.getClass())
                    || value.getClass().isArray() && offsetField.field.getType().isArray())
                theUnsafe.putObject(parent, offsetField.offset, value);
            else
                throw new AttributeTypeMismatchException(AttributeTypeMismatchException.ATTRIBUTE_TYPE_MISMATCH, value.getClass(), offsetField.field.getType(), offsetField.name);
            return;
        }

        offsetField.field.set(parent, value);
    }

    /**
     * Reflection utility for setting an attribute
     *
     * @param parent
     * @param child
     * @param field
     */
    public static void setAny(Object parent, Object child, OffsetField field) throws AttributeMissingException,AttributeTypeMismatchException
    {
        final Class toType = field.type;
        Class fromType = null;

        if(child != null)
            fromType = child.getClass();

        try {
            if (toType == String.class && fromType != String.class && child != null)
                setObject(parent, field, child.toString());
            else if (toType == Long.class) {
                if (fromType == Long.class)
                    setObject(parent, field, child);
                else if (fromType == long.class)
                    setObject(parent, field, child);
                else if (fromType == Integer.class)
                    setObject(parent, field, ((Integer) child).longValue());
                else if (fromType == int.class)
                    setObject(parent, field, child);
                else if (fromType == Boolean.class)
                    setObject(parent, field, ((Boolean) child) ? new Long(1) : new Long(0));
                else if (fromType == boolean.class)
                    setObject(parent, field, ((boolean) child) ? new Long(1) : new Long(0));
                else if (child == null)
                    setObject(parent, field, child);
                else
                    setObject(parent, field, toType.cast(child));
            } else if (toType == Integer.class) {
                if (fromType == Long.class)
                    setObject(parent, field, ((Long) child).intValue());
                else if (fromType == long.class)
                    setObject(parent, field, new Integer((int) child));
                else if (fromType == Integer.class)
                    setObject(parent, field, child);
                else if (fromType == int.class)
                    setObject(parent, field, new Integer((int) child));
                else if (fromType == Boolean.class)
                    setObject(parent, field, ((Boolean) child) ? new Integer(1) : new Integer(0));
                else if (fromType == boolean.class)
                    setObject(parent, field, ((boolean) child) ? new Integer(1) : new Integer(0));
                else if (child == null)
                    setObject(parent, field, child);
                else
                    setObject(parent, field, toType.cast(child));
            } else if (toType == Double.class) {
                if (fromType == Float.class)
                    setObject(parent, field, new Double(((Float) child).doubleValue()));
                else if (fromType == float.class)
                    setObject(parent, field, new Double((double) child));
                else if (fromType == Double.class)
                    setObject(parent, field, child);
                else if (fromType == double.class)
                    setObject(parent, field, new Double((double) child));
                else if (fromType == Integer.class)
                    setObject(parent, field, new Double(((Integer) child).doubleValue()));
                else if (fromType == int.class)
                    setObject(parent, field, new Double(new Integer((int) child).doubleValue()));
                else if (fromType == Long.class)
                    setObject(parent, field, new Double(((Long) child).doubleValue()));
                else if (fromType == long.class)
                    setDouble(parent, field, new Double(new Long((long) child).doubleValue()));
                else if (child == null)
                    setObject(parent, field, child);
                else
                    setObject(parent, field, toType.cast(child));
            } else if (toType == Float.class) {
                if (fromType == Float.class)
                    setObject(parent, field, ((Float) child));
                else if (fromType == float.class)
                    setObject(parent, field, new Float((float) child));
                else if (fromType == Double.class)
                    setObject(parent, field, child);
                else if (fromType == double.class)
                    setObject(parent, field, new Float((float) child));
                else if (fromType == Integer.class)
                    setObject(parent, field, new Float(((Integer) child).floatValue()));
                else if (fromType == int.class)
                    setObject(parent, field, new Float(new Integer((int) child).floatValue()));
                else if (fromType == Long.class)
                    setObject(parent, field, new Float(((Long) child).floatValue()));
                else if (fromType == long.class)
                    setDouble(parent, field, new Float(new Long((long) child).floatValue()));
                else if (child == null)
                    setObject(parent, field, child);
                else
                    setObject(parent, field, toType.cast(child));
            } else if (toType == Boolean.class) {
                if (fromType == boolean.class)
                    setObject(parent, field, new Boolean((boolean) child));
                else if (fromType == Boolean.class)
                    setObject(parent, field, child);
                else if (fromType == Integer.class)
                    setObject(parent, field, new Boolean((((Integer) child).intValue() == 1) ? true : false));
                else if (fromType == int.class)
                    setObject(parent, field, new Boolean(((int) child == 1) ? true : false));
                else if (fromType == Long.class)
                    setObject(parent, field, new Boolean(((Long) child == 1) ? true : false));
                else if (fromType == long.class)
                    setObject(parent, field, new Boolean(((long) child == 1) ? true : false));
                else if (child == null)
                    setObject(parent, field, child);
                else
                    setObject(parent, field, toType.cast(child));
            } else if (toType == int.class) {
                if (fromType == Long.class)
                    setInt(parent, field, ((Long) child).intValue());
                else if (fromType == long.class)
                    setInt(parent, field, (int) child);
                else if (fromType == Integer.class)
                    setInt(parent, field, ((Integer) child).intValue());
                else if (fromType == int.class)
                    setInt(parent, field, (int) child);
                else if (fromType == Boolean.class)
                    setInt(parent, field, ((Boolean) child) ? 1 : 0);
                else if (fromType == boolean.class)
                    setInt(parent, field, ((boolean) child) ? 1 : 0);
                else if (child == null)
                    setInt(parent, field, 0);
                else
                    setInt(parent, field, (int) child);
            } else if (toType == long.class) {
                if (fromType == Long.class)
                    setLong(parent, field, ((Long) child).longValue());
                else if (fromType == long.class)
                    setLong(parent, field, (long) child);
                else if (fromType == Integer.class)
                    setLong(parent, field, ((Integer) child).longValue());
                else if (fromType == Boolean.class)
                    setLong(parent, field, ((Boolean) child) ? 1 : 0);
                else if (fromType == boolean.class)
                    setLong(parent, field, ((boolean) child) ? 1 : 0);
                else if (child == null)
                    setLong(parent, field, 0l);
                else
                    setLong(parent, field, (long) child);
            } else if (toType == boolean.class) {
                if (fromType == boolean.class)
                    setBoolean(parent, field, (boolean) child);
                else if (fromType == Boolean.class)
                    setBoolean(parent, field, ((Boolean) child).booleanValue());
                else if (fromType == Integer.class)
                    setBoolean(parent, field, (((Integer) child).intValue() == 1) ? true : false);
                else if (fromType == int.class)
                    setBoolean(parent, field, ((int) child == 1) ? true : false);
                else if (fromType == Long.class)
                    setBoolean(parent, field, ((Long) child == 1) ? true : false);
                else if (fromType == long.class)
                    setBoolean(parent, field, ((long) child == 1) ? true : false);
                else if (child == null)
                    setBoolean(parent, field, false);
                else
                    setBoolean(parent, field, (boolean) toType.cast(child));
            } else if (toType == double.class) {
                if (fromType == Float.class)
                    setDouble(parent, field, ((Float) child).doubleValue());
                else if (fromType == float.class)
                    setDouble(parent, field, (double) child);
                else if (fromType == Double.class)
                    setDouble(parent, field, ((Double) child).doubleValue());
                else if (fromType == double.class)
                    setDouble(parent, field, (double) child);
                else if (fromType == Integer.class)
                    setDouble(parent, field, ((Integer) child).doubleValue());
                else if (fromType == int.class)
                    setDouble(parent, field, new Integer((int) child).doubleValue());
                else if (fromType == Long.class)
                    setDouble(parent, field, ((Long) child).doubleValue());
                else if (fromType == long.class)
                    setDouble(parent, field, new Long((long) child).doubleValue());
                else if (child == null)
                    setDouble(parent, field, 0.0);
                else
                    setDouble(parent, field, (double) child);
            } else if (toType == float.class) {
                if (fromType == Float.class)
                    setFloat(parent, field, ((Float) child).floatValue());
                else if (fromType == float.class)
                    setFloat(parent, field, (float) child);
                else if (fromType == Double.class)
                    setFloat(parent, field, ((Float) child).floatValue());
                else if (fromType == double.class)
                    setFloat(parent, field, (float) child);
                else if (fromType == Integer.class)
                    setFloat(parent, field, ((Integer) child).floatValue());
                else if (fromType == int.class)
                    setFloat(parent, field, new Integer((int) child).floatValue());
                else if (fromType == Long.class)
                    setFloat(parent, field, ((Long) child).floatValue());
                else if (fromType == long.class)
                    setFloat(parent, field, new Long((long) child).floatValue());
                else if (child == null)
                    setFloat(parent, field, 0);
                else
                    setFloat(parent, field, (float) child);
            } else if (fromType != toType)
                setObject(parent, field, toType.cast(child));
            else
                setObject(parent, field, child);
        }
        catch (IllegalAccessException illegalAccessException)
        {
            // This is supressed
        }
    }

    /**
     * Returns a copy of the object, or null if the object cannot
     * be serialized.
     */
    public static void copy(IManagedEntity orig, IManagedEntity dest, EntityDescriptor descriptor)
    {
        // Copy all attributes
        for (AttributeDescriptor attribute : descriptor.getAttributes().values())
        {
            OffsetField field = null;
            try
            {
                field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
            } catch (Exception e)
            {
                try
                {
                    field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                    ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
                } catch (Exception e1)
                {
                }
            }
        }

        descriptor.getRelationships().values().stream().forEach(attribute ->
        {
            OffsetField field = null;
            try
            {
                field = ReflectionUtil.getOffsetField(orig.getClass(),attribute.getName());
                ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
            } catch (Exception e)
            {
                try
                {
                    field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                    ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
                } catch (Exception e1)
                {
                }
            }
        });
    }
}
