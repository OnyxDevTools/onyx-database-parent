package com.onyx.util;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.persistence.IManagedEntity;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Byte utilities
 */
public class ObjectUtil {

    private static ObjectUtil instance;
    private Unsafe unsafe = null;

    public synchronized static ObjectUtil getInstance()
    {
        if (instance == null)
        {
            instance = new ObjectUtil();
            try
            {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                instance.unsafe = (Unsafe) f.get(null);
            } catch (NoSuchFieldException e)
            {
            } catch (IllegalAccessException e)
            {
            }
        }
        return instance;
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
            AttributeField field = null;
            try
            {
                field = new AttributeField(ObjectUtil.getField(orig.getClass(), attribute.getName()));
                instance.setAttribute(dest, instance.getAttribute(field, orig), field);
            } catch (Exception e)
            {
                try
                {
                    field = ObjectUtil.getAttributeField(orig.getClass(), attribute.getName());
                    instance.setAttribute(dest, instance.getAttribute(field, orig), field);
                } catch (Exception e1)
                {
                }
            }
        }

        descriptor.getRelationships().values().stream().forEach(attribute ->
        {
            AttributeField field = null;
            try
            {
                field = new AttributeField(ObjectUtil.getField(orig.getClass(),attribute.getName()));
                instance.setAttribute(dest, instance.getAttribute(field, orig), field);
            } catch (Exception e)
            {
                try
                {
                    field = ObjectUtil.getAttributeField(orig.getClass(), attribute.getName());
                    instance.setAttribute(dest, instance.getAttribute(field, orig), field);
                } catch (Exception e1)
                {
                }
            }
        });
    }

    /**
     * Reflection utility for Get Attribute
     *
     * @param field
     * @param obj
     * @return
     */
    public Object getAttribute(AttributeField field, Object obj)
    {
        final Class type = field.type;

        if (type == int.class)
            return unsafe.getInt(obj, field.offset);
        else if (type == long.class)
            return unsafe.getLong(obj, field.offset);
        else if (type == boolean.class)
            return unsafe.getBoolean(obj, field.offset);
        else if (type == double.class)
            return unsafe.getDouble(obj, field.offset);

        return unsafe.getObject(obj, field.offset);
    }

    /**
     * Gets the offset for a field
     *
     * @param field
     * @return
     */
    public long getAttributeOffset(Field field)
    {
        return unsafe.objectFieldOffset(field);
    }

    /**
     * Reflection utility for setting an attribute
     *
     * @param parent
     * @param child
     * @param field
     */
    public void setAttribute(Object parent, Object child, AttributeField field) throws AttributeMissingException
    {
        final Class toType = field.type;
        Class fromType = null;

        if(child != null)
            fromType = child.getClass();

        if(toType == String.class && fromType != String.class && child != null)
            unsafe.putObject(parent, field.offset, child.toString());
        else if(toType == Long.class)
        {
            if(fromType == Long.class)
                unsafe.putObject(parent, field.offset, child);
            else if(fromType == long.class)
                unsafe.putObject(parent, field.offset, child);
            else if(fromType == Integer.class)
                unsafe.putObject(parent, field.offset, ((Integer)child).longValue());
            else if(fromType == int.class)
                unsafe.putObject(parent, field.offset, child);
            else if(fromType == Boolean.class)
                unsafe.putObject(parent, field.offset, ((Boolean) child) ? new Long(1) : new Long(0));
            else if(fromType == boolean.class)
                unsafe.putObject(parent, field.offset, ((boolean) child) ? new Long(1) : new Long(0));
            else if(child == null)
                unsafe.putObject(parent, field.offset, child);
            else
                unsafe.putObject(parent, field.offset, toType.cast(child));
        }
        else if(toType == Integer.class)
        {
            if(fromType == Long.class)
                unsafe.putObject(parent, field.offset, ((Long)child).intValue());
            else if(fromType == long.class)
                unsafe.putObject(parent, field.offset, new Integer((int)child));
            else if(fromType == Integer.class)
                unsafe.putObject(parent, field.offset, child);
            else if(fromType == int.class)
                unsafe.putObject(parent, field.offset, new Integer((int)child));
            else if(fromType == Boolean.class)
                unsafe.putObject(parent, field.offset, ((Boolean) child) ? new Integer(1) : new Integer(0));
            else if(fromType == boolean.class)
                unsafe.putObject(parent, field.offset, ((boolean) child) ? new Integer(1) : new Integer(0));
            else if(child == null)
                unsafe.putObject(parent, field.offset, child);
            else
                unsafe.putObject(parent, field.offset, toType.cast(child));
        }
        else if(toType == Double.class)
        {
            if(fromType == Float.class)
                unsafe.putObject(parent, field.offset, new Double(((Float)child).doubleValue()));
            else if(fromType == float.class)
                unsafe.putObject(parent, field.offset, new Double((double)child));
            else if(fromType == Double.class)
                unsafe.putObject(parent, field.offset, child);
            else if(fromType == double.class)
                unsafe.putObject(parent, field.offset, new Double((double) child));
            else if(fromType == Integer.class)
                unsafe.putObject(parent, field.offset, new Double(((Integer) child).doubleValue()));
            else if(fromType == int.class)
                unsafe.putObject(parent, field.offset, new Double(new Integer((int) child).doubleValue()));
            else if(fromType == Long.class)
                unsafe.putObject(parent, field.offset, new Double(((Long)child).doubleValue()));
            else if(fromType == long.class)
                unsafe.putDouble(parent, field.offset, new Double(new Long((long)child).doubleValue()));
            else if(child == null)
                unsafe.putObject(parent, field.offset, child);
            else
                unsafe.putObject(parent, field.offset, toType.cast(child));
        }
        else if(toType == Float.class)
        {
            if(fromType == Float.class)
                unsafe.putObject(parent, field.offset, ((Float)child));
            else if(fromType == float.class)
                unsafe.putObject(parent, field.offset, new Float((float)child));
            else if(fromType == Double.class)
                unsafe.putObject(parent, field.offset, child);
            else if(fromType == double.class)
                unsafe.putObject(parent, field.offset, new Float((float)child));
            else if(fromType == Integer.class)
                unsafe.putObject(parent, field.offset, new Float(((Integer) child).floatValue()));
            else if(fromType == int.class)
                unsafe.putObject(parent, field.offset, new Float(new Integer((int) child).floatValue()));
            else if(fromType == Long.class)
                unsafe.putObject(parent, field.offset, new Float(((Long) child).floatValue()));
            else if(fromType == long.class)
                unsafe.putDouble(parent, field.offset, new Float(new Long((long)child).floatValue()));
            else if(child == null)
                unsafe.putObject(parent, field.offset, child);
            else
                unsafe.putObject(parent, field.offset, toType.cast(child));
        }
        else if (toType == Boolean.class) {
            if (fromType == boolean.class)
                unsafe.putObject(parent, field.offset, new Boolean((boolean) child));
            else if (fromType == Boolean.class)
                unsafe.putObject(parent, field.offset, child);
            else if (fromType == Integer.class)
                unsafe.putObject(parent, field.offset, new Boolean((((Integer) child).intValue() == 1) ? true : false));
            else if (fromType == int.class)
                unsafe.putObject(parent, field.offset, new Boolean(((int) child == 1) ? true : false));
            else if (fromType == Long.class)
                unsafe.putObject(parent, field.offset, new Boolean(((Long) child == 1) ? true : false));
            else if (fromType == long.class)
                unsafe.putObject(parent, field.offset, new Boolean(((long)child == 1) ? true : false));
            else if(child == null)
                unsafe.putObject(parent, field.offset, child);
            else
                unsafe.putObject(parent, field.offset, toType.cast(child));
        }
        else if (toType == int.class) {
            if (fromType == Long.class)
                unsafe.putInt(parent, field.offset, ((Long) child).intValue());
            else if (fromType == long.class)
                unsafe.putInt(parent, field.offset, (int) child);
            else if (fromType == Integer.class)
                unsafe.putInt(parent, field.offset, ((Integer) child).intValue());
            else if (fromType == int.class)
                unsafe.putInt(parent, field.offset, (int) child);
            else if (fromType == Boolean.class)
                unsafe.putInt(parent, field.offset, ((Boolean) child) ? 1 : 0);
            else if(fromType == boolean.class)
                unsafe.putInt(parent, field.offset, ((boolean) child) ? 1 : 0);
            else if(child == null)
                unsafe.putInt(parent, field.offset, 0);
            else
                unsafe.putInt(parent, field.offset, (int) child);
        }
        else if (toType == long.class) {
            if (fromType == Long.class)
                unsafe.putLong(parent, field.offset, ((Long) child).longValue());
            else if (fromType == long.class)
                unsafe.putLong(parent, field.offset, (long) child);
            else if (fromType == Integer.class)
                unsafe.putLong(parent, field.offset, ((Integer) child).longValue());
            else if (fromType == Boolean.class)
                unsafe.putLong(parent, field.offset, ((Boolean) child) ? 1 : 0);
            else if (fromType == boolean.class)
                unsafe.putLong(parent, field.offset, ((boolean) child) ? 1 : 0);
            else if(child == null)
                unsafe.putLong(parent, field.offset, 0l);
            else
                unsafe.putLong(parent, field.offset, (long)child);
        }
        else if (toType == boolean.class)
        {
            if (fromType == boolean.class)
                unsafe.putBoolean(parent, field.offset, (boolean) child);
            else if (fromType == Boolean.class)
                unsafe.putBoolean(parent, field.offset, ((Boolean) child).booleanValue());
            else if (fromType == Integer.class)
                unsafe.putBoolean(parent, field.offset, (((Integer) child).intValue() == 1) ? true : false);
            else if (fromType == int.class)
                unsafe.putBoolean(parent, field.offset, ((int) child == 1) ? true : false);
            else if (fromType == Long.class)
                unsafe.putBoolean(parent, field.offset, ((Long) child == 1) ? true : false);
            else if (fromType == long.class)
                unsafe.putBoolean(parent, field.offset, ((long) child == 1) ? true : false);
            else if(child == null)
                unsafe.putBoolean(parent, field.offset, false);
            else
                unsafe.putBoolean(parent, field.offset, (boolean)toType.cast(child));
        }
        else if (toType == double.class) {
            if (fromType == Float.class)
                unsafe.putDouble(parent, field.offset, ((Float) child).doubleValue());
            else if (fromType == float.class)
                unsafe.putDouble(parent, field.offset, (double) child);
            else if (fromType == Double.class)
                unsafe.putDouble(parent, field.offset, ((Double) child).doubleValue());
            else if (fromType == double.class)
                unsafe.putDouble(parent, field.offset, (double) child);
            else if (fromType == Integer.class)
                unsafe.putDouble(parent, field.offset, ((Integer) child).doubleValue());
            else if (fromType == int.class)
                unsafe.putDouble(parent, field.offset, new Integer((int) child).doubleValue());
            else if (fromType == Long.class)
                unsafe.putDouble(parent, field.offset, ((Long) child).doubleValue());
            else if (fromType == long.class)
                unsafe.putDouble(parent, field.offset, new Long((long) child).doubleValue());
            else if(child == null)
                unsafe.putDouble(parent, field.offset, 0.0);
            else
                unsafe.putDouble(parent, field.offset, (double) child);
        }
        else if (toType == float.class) {
            if(fromType == Float.class)
                unsafe.putFloat(parent, field.offset, ((Float) child).floatValue());
            else if(fromType == float.class)
                unsafe.putFloat(parent, field.offset, (float) child);
            else if(fromType == Double.class)
                unsafe.putFloat(parent, field.offset, ((Float) child).floatValue());
            else if(fromType == double.class)
                unsafe.putFloat(parent, field.offset, (float) child);
            else if(fromType == Integer.class)
                unsafe.putFloat(parent, field.offset, ((Integer) child).floatValue());
            else if(fromType == int.class)
                unsafe.putFloat(parent, field.offset, new Integer((int) child).floatValue());
            else if(fromType == Long.class)
                unsafe.putFloat(parent, field.offset, ((Long) child).floatValue());
            else if(fromType == long.class)
                unsafe.putFloat(parent, field.offset, new Long((long) child).floatValue());
            else if(child == null)
                unsafe.putFloat(parent, field.offset, 0);
            else
                unsafe.putFloat(parent, field.offset, (float) child);
        }
        else if(fromType != toType)
            unsafe.putObject(parent, field.offset, toType.cast(child));
        else
            unsafe.putObject(parent, field.offset, child);
    }

    /**
     * Helper for getting field that may be an inherited field
     *
     * @param clazz
     * @param attribute
     * @return
     * @throws AttributeMissingException
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
     * Helper for getting field that may be an inherited field
     *
     * @param clazz
     * @param attribute
     * @return
     * @throws AttributeMissingException
     */
    public static AttributeField getAttributeField(Class clazz, String attribute) throws AttributeMissingException
    {
        Field f = getField(clazz, attribute);
        return new AttributeField(f);
    }
}