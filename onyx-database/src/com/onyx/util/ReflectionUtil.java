package com.onyx.util;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.SynchronizedMap;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created by tosborn1 on 8/2/16.
 * <p>
 * The purpose of this class is to encapsulate how we are performing reflection.  The default way is to use the unsafe api
 * but if it is not available, we default to generic reflection.
 *
 * @since 1.2.2 This class has been refactored to remove theUnsafe.  That is unavailable and it did not prooove to provide
 * performance benefit.
 */
@SuppressWarnings("unchecked")
public class ReflectionUtil {


    // Cache of class' fields
    private static final CompatMap<Class, List<OffsetField>> classFields = new SynchronizedMap<>(new CompatWeakHashMap<>());


    /**
     * Get all the fields to serialize
     *
     * @param object The object to read the fields from
     * @return Fields with their offsets
     */
    public static List<OffsetField> getFields(Object object) {
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
                                    fields.add(new OffsetField(-1, f.getName(), f));
                            } else if (f.isAnnotationPresent(Attribute.class)
                                    || f.isAnnotationPresent(Relationship.class)) {
                                    fields.add(new OffsetField(-1, f.getName(), f));
                            }
                        }
                    }
                    aClass = aClass.getSuperclass();
                }

                Collections.sort(fields, (o1, o2) -> o1.name.compareTo(o2.name));
            }
            return fields;
        });
    }

    /**
     * Helper for getting field that may be an inherited field
     *
     * @param clazz     Parent class to reflect
     * @param attribute Attribute Name
     * @return Offset field which is a wrapper so that it can use Unsafe for reflection if it is available
     * @throws AttributeMissingException The attribute does not exist
     */
    public static OffsetField getOffsetField(Class clazz, String attribute) throws AttributeMissingException {
        Field field = getField(clazz, attribute);
        return getOffsetField(field);
    }

    /**
     * Helper for getting the offset field which is a wrapper so that it can be used by the Unsafe for reflection
     *
     * @param field Reflect field to wrap
     * @return The offset field
     */
    public static OffsetField getOffsetField(Field field) {
        return new OffsetField(-1, field.getName(), field);
    }

    /**
     * Helper for getting field that may be an inherited field
     *
     * @param clazz     Parent class to reflect upon
     * @param attribute Attribute to get field of
     * @return The Field that corresponds to that attribute name
     * @throws AttributeMissingException Exception thrown when the field is not there
     */
    public static Field getField(Class clazz, String attribute) throws AttributeMissingException {
        while (clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField(attribute);
                if (f != null) {
                    if (!f.isAccessible()) {
                        f.setAccessible(true);
                    }
                    return f;
                }
            } catch (NoSuchFieldException e) {
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
     * @return the fully instantiated object
     * @throws InstantiationException Exception thrown when using unsafe to allocate an instance
     * @throws IllegalAccessException Exception thrown when using regular reflection
     */
    public static Object instantiate(Class type) throws InstantiationException, IllegalAccessException {
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
     * @param parent      The object to get the int field from
     * @param offsetField The field to reflect on
     * @return a primitive int
     */
    public static int getInt(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getInt(parent);
    }

    /**
     * Get byte for an object and a field
     *
     * @param parent      The object to get the byte field from
     * @param offsetField The field to reflect on
     * @return a primitive byte
     */
    public static byte getByte(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getByte(parent);
    }

    /**
     * Get long for an object and a field
     *
     * @param parent      The object to get the long field from
     * @param offsetField The field to reflect on
     * @return a primitive long
     */
    public static long getLong(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getLong(parent);
    }

    /**
     * Get float for an object and a field
     *
     * @param parent      The object to get the float field from
     * @param offsetField The field to reflect on
     * @return a primitive float
     */
    public static float getFloat(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getFloat(parent);
    }

    /**
     * Get double for an object and a field
     *
     * @param parent      The object to get the double field from
     * @param offsetField The field to reflect on
     * @return a primitive double
     */
    public static double getDouble(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getDouble(parent);
    }

    /**
     * Get short for an object and a field
     *
     * @param parent      The object to get the boolean field from
     * @param offsetField The field to reflect on
     * @return a primitive boolean
     */
    public static boolean getBoolean(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getBoolean(parent);
    }

    /**
     * Get short for an object and a field
     *
     * @param parent      The object to get the short field from
     * @param offsetField The field to reflect on
     * @return a primitive short
     */
    public static short getShort(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getShort(parent);
    }

    /**
     * Get char for an object and a field
     *
     * @param parent      The object to get the char field from
     * @param offsetField The field to reflect on
     * @return a primitive char
     */
    public static char getChar(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.getChar(parent);
    }

    /**
     * Get object for an object and a field
     *
     * @param parent      The object to get the object field from
     * @param offsetField The field to reflect on
     * @return a mutable object of any kind
     */
    public static Object getObject(Object parent, OffsetField offsetField) throws IllegalAccessException {
        return offsetField.field.get(parent);
    }

    /**
     * This method is to return any key from a field using reflection.  It
     * can either return a primitive or an object.  Note: If inteneded to get a
     * primitive, I recommend using the other api methods to avoid autoboxing.
     *
     * @param object      Parent object
     * @param offsetField field to get
     * @return field key
     */
    @SuppressWarnings("RedundantThrows")
    public static Object getAny(Object object, OffsetField offsetField) throws AttributeTypeMismatchException {
        try {


            final PropertyType fieldType = PropertyType.primitiveValueOf(offsetField.type);
            switch (fieldType) {
                case INT:
                    return ReflectionUtil.getInt(object, offsetField);
                case LONG:
                    return ReflectionUtil.getLong(object, offsetField);
                case BYTE:
                    return ReflectionUtil.getByte(object, offsetField);
                case FLOAT:
                    return ReflectionUtil.getFloat(object, offsetField);
                case DOUBLE:
                    return ReflectionUtil.getDouble(object, offsetField);
                case BOOLEAN:
                    return ReflectionUtil.getBoolean(object, offsetField);
                case SHORT:
                    return ReflectionUtil.getShort(object, offsetField);
                case CHAR:
                    return ReflectionUtil.getChar(object, offsetField);
                default:
                    return ReflectionUtil.getObject(object, offsetField);

            }
        } catch (IllegalAccessException e) {
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
     * @param parent      The object to put the int on
     * @param offsetField The field to reflect on
     * @param value       int key to set
     */
    public static void setInt(Object parent, OffsetField offsetField, int value) throws IllegalAccessException {
        offsetField.field.setInt(parent, value);
    }

    /**
     * Put an long on an parent object
     *
     * @param parent      The object to put the long on
     * @param offsetField The field to reflect on
     * @param value       long key to set
     */
    public static void setLong(Object parent, OffsetField offsetField, long value) throws IllegalAccessException {
        offsetField.field.setLong(parent, value);
    }

    /**
     * Put an byte on an parent object
     *
     * @param parent      The object to put the byte on
     * @param offsetField The field to reflect on
     * @param value       byte key to set
     */
    public static void setByte(Object parent, OffsetField offsetField, byte value) throws IllegalAccessException {
        offsetField.field.setByte(parent, value);
    }

    /**
     * Put an float on an parent object
     *
     * @param parent      The object to put the float on
     * @param offsetField The field to reflect on
     * @param value       float key to set
     */
    public static void setFloat(Object parent, OffsetField offsetField, float value) throws IllegalAccessException {
        offsetField.field.setFloat(parent, value);
    }

    /**
     * Put an double on an parent object
     *
     * @param parent      The object to put the double on
     * @param offsetField The field to reflect on
     * @param value       double key to set
     */
    public static void setDouble(Object parent, OffsetField offsetField, double value) throws IllegalAccessException {
        offsetField.field.setDouble(parent, value);
    }

    /**
     * Put an short on an parent object
     *
     * @param parent      The object to put the short on
     * @param offsetField The field to reflect on
     * @param value       short key to set
     */
    public static void setShort(Object parent, OffsetField offsetField, short value) throws IllegalAccessException {
        offsetField.field.setShort(parent, value);
    }

    /**
     * Put an boolean on an parent object
     *
     * @param parent      The object to put the boolean on
     * @param offsetField The field to reflect on
     * @param value       boolean key to set
     */
    public static void setBoolean(Object parent, OffsetField offsetField, boolean value) throws IllegalAccessException {
        offsetField.field.setBoolean(parent, value);
    }

    /**
     * Put an char on an parent object
     *
     * @param parent      The object to put the char on
     * @param offsetField The field to reflect on
     * @param value       char key to set
     */
    public static void setChar(Object parent, OffsetField offsetField, char value) throws IllegalAccessException {
        offsetField.field.setChar(parent, value);
    }

    /**
     * Put an object on an parent object
     *
     * @param parent      The object to put the object on
     * @param offsetField The field to reflect on
     * @param value       object key to set
     */
    public static void setObject(Object parent, OffsetField offsetField, Object value) throws IllegalAccessException {
        offsetField.field.set(parent, value);
    }


    /**
     * Reflection utility for setting an attribute
     *
     * @param parent Parent object to set property on
     * @param child Child object that is the property value
     * @param field Field to set on the parent
     */
    @SuppressWarnings({"ConstantConditions", "RedundantThrows"})
    public static void setAny(Object parent, Object child, OffsetField field) throws AttributeMissingException {


        try {

            final Class toClass = field.type;
            final PropertyType toType = PropertyType.valueOf(field.type);
            final PropertyType fromType = (child == null) ? null : PropertyType.valueOf(child.getClass());

            switch (toType) {

                case MUTABLE_INT:
                    switch (fromType) {
                        case MUTABLE_INT:
                        case INT:
                        case LONG:
                        case NULL:
                            setObject(parent, field, child);
                            break;
                        case MUTABLE_LONG:
                            setObject(parent, field, ((Long) child).intValue());
                            break;
                        case MUTABLE_BOOLEAN:
                        case BOOLEAN:
                            setObject(parent, field, ((boolean) child) ? new Integer(1) : new Integer(0));
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case INT:
                    switch (fromType) {
                        case MUTABLE_INT:
                        case INT:
                        case LONG:
                            setInt(parent, field, (int)child);
                            break;
                        case MUTABLE_LONG:
                            setInt(parent, field, ((Long) child).intValue());
                            break;
                        case MUTABLE_BOOLEAN:
                        case BOOLEAN:
                            setInt(parent, field, ((boolean) child) ? 1 : 0);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case MUTABLE_LONG:
                    switch (fromType) {
                        case LONG:
                        case MUTABLE_LONG:
                        case INT:
                        case NULL:
                            setObject(parent, field, child);
                            break;
                        case MUTABLE_INT:
                            setObject(parent, field, ((Integer) child).longValue());
                            break;
                        case MUTABLE_BOOLEAN:
                        case BOOLEAN:
                            setObject(parent, field, ((boolean) child) ? new Long(1) : new Long(0));
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case LONG:
                    switch (fromType) {
                        case LONG:
                        case MUTABLE_LONG:
                        case INT:
                            setLong(parent, field, (long)child);
                            break;
                        case MUTABLE_INT:
                            setLong(parent, field, ((Integer) child).longValue());
                            break;
                        case BOOLEAN:
                        case MUTABLE_BOOLEAN:
                            setLong(parent, field, ((boolean) child) ? 1L : 0L);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                    }
                    break;
                case MUTABLE_DOUBLE:
                    switch (fromType) {
                        case MUTABLE_DOUBLE:
                        case DOUBLE:
                            setObject(parent, field, child);
                            break;
                        case FLOAT:
                        case MUTABLE_FLOAT:
                            setObject(parent, field, ((Float) child).doubleValue());
                            break;
                        case NULL:
                            setObject(parent, field, null);
                            break;
                        case MUTABLE_INT:
                            setObject(parent, field, ((Integer) child).doubleValue());
                            break;
                        case INT:
                            setObject(parent, field, new Integer((int) child).doubleValue());
                            break;
                        case MUTABLE_LONG:
                            setObject(parent, field, ((Long) child).doubleValue());
                            break;
                        case LONG:
                            setDouble(parent, field, new Long((long) child).doubleValue());
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case DOUBLE:
                    switch (fromType) {
                        case MUTABLE_DOUBLE:
                            setDouble(parent, field, (Double) child);
                            break;
                        case DOUBLE:
                        case FLOAT:
                            setDouble(parent, field, (double) child);
                            break;
                        case MUTABLE_FLOAT:
                            setDouble(parent, field, ((Float) child).doubleValue());
                            break;
                        case MUTABLE_INT:
                            setDouble(parent, field, ((Integer) child).doubleValue());
                            break;
                        case INT:
                            setDouble(parent, field, new Integer((int) child).doubleValue());
                            break;
                        case MUTABLE_LONG:
                            setDouble(parent, field, ((Long) child).doubleValue());
                            break;
                        case LONG:
                            setDouble(parent, field, new Long((long) child).doubleValue());
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case BOOLEAN:
                    switch (fromType) {
                        case BOOLEAN:
                        case MUTABLE_BOOLEAN:
                            setBoolean(parent, field, (boolean)child);
                            break;
                        case MUTABLE_INT:
                            setBoolean(parent, field, (Integer) child == 1);
                            break;
                        case INT:
                            setBoolean(parent, field, ((int) child == 1));
                            break;
                        case MUTABLE_LONG:
                            setBoolean(parent, field, (Long) child == 1);
                            break;
                        case LONG:
                            setBoolean(parent, field, ((long) child == 1));
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case MUTABLE_BOOLEAN:
                    switch (fromType) {
                        case BOOLEAN:
                        case MUTABLE_BOOLEAN:
                            setObject(parent, field, child);
                            break;
                        case MUTABLE_INT:
                        case INT:
                            setObject(parent, field, ((int) child == 1));
                            break;
                        case MUTABLE_LONG:
                        case LONG:
                            setObject(parent, field, ((long) child == 1));
                            break;
                        case NULL:
                            setObject(parent, field, child);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case MUTABLE_BYTE:
                    switch (fromType)
                    {
                        case MUTABLE_BYTE:
                        case BYTE:
                            setObject(parent, field, child);
                            break;
                        case INT:
                        case MUTABLE_INT:
                            setObject(parent, field, ((Integer) child).byteValue());
                            break;
                        case BOOLEAN:
                            setObject(parent, field, ((boolean) child) ? new Byte((byte)1) : new Byte((byte)0));
                            break;
                        case MUTABLE_BOOLEAN:
                            setObject(parent, field, ((Boolean) child) ? new Byte((byte)1) : new Byte((byte)0));
                            break;
                        case NULL:
                            setObject(parent, field, null);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case BYTE:
                    switch (fromType)
                    {
                        case MUTABLE_BYTE:
                        case BYTE:
                            setByte(parent, field, (byte)child);
                            break;
                        case INT:
                        case MUTABLE_INT:
                            setByte(parent, field, ((Integer) child).byteValue());
                            break;
                        case BOOLEAN:
                            setByte(parent, field, ((boolean) child) ? new Byte((byte)1) : new Byte((byte)0));
                            break;
                        case MUTABLE_BOOLEAN:
                            setByte(parent, field, ((Boolean) child) ? new Byte((byte)1) : new Byte((byte)0));
                            break;
                    }
                    break;
                case MUTABLE_SHORT:
                    switch (fromType)
                    {
                        case MUTABLE_LONG:
                            setObject(parent, field, ((Long) child).shortValue());
                            break;
                        case LONG:
                        case SHORT:
                        case INT:
                            setObject(parent, field, child);
                            break;
                        case MUTABLE_INT:
                            setObject(parent, field, ((Integer)child).shortValue());
                            break;
                        case MUTABLE_BOOLEAN:
                            setObject(parent, field, ((Boolean) child) ? new Short((short)1) : new Short((short)0));
                            break;
                        case BOOLEAN:
                            setObject(parent, field, ((boolean) child) ? new Short((short)1) : new Short((short)0));
                            break;
                        case NULL:
                            setObject(parent, field, null);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case SHORT:
                    switch (fromType)
                    {
                        case MUTABLE_LONG:
                            setShort(parent, field, ((Long) child).shortValue());
                            break;
                        case LONG:
                        case SHORT:
                        case MUTABLE_SHORT:
                        case INT:
                            setShort(parent, field, (short)child);
                            break;
                        case MUTABLE_INT:
                            setShort(parent, field, ((Integer)child).shortValue());
                            break;
                        case MUTABLE_BOOLEAN:
                            setShort(parent, field, ((Boolean) child) ? new Short((short)1) : new Short((short)0));
                            break;
                        case BOOLEAN:
                            setShort(parent, field, ((boolean) child) ? new Short((short)1) : new Short((short)0));
                            break;
                        default:
                            setShort(parent, field, (short)toClass.cast(child));
                            break;
                    }
                    break;
                case FLOAT:
                    switch (fromType)
                    {
                        case MUTABLE_FLOAT:
                        case FLOAT:
                        case MUTABLE_DOUBLE:
                        case DOUBLE:
                            setFloat(parent, field, (float)child);
                            break;
                        case MUTABLE_INT:
                            setFloat(parent, field, ((Integer) child).floatValue());
                            break;
                        case INT:
                            setFloat(parent, field, new Integer((int) child).floatValue());
                            break;
                        case MUTABLE_LONG:
                            setFloat(parent, field, ((Long) child).floatValue());
                            break;
                        case LONG:
                            setFloat(parent, field, new Long((long) child).floatValue());
                            break;
                        default:
                            setFloat(parent, field, (float)toClass.cast(child));
                            break;
                    }
                    break;
                case MUTABLE_FLOAT:
                    switch (fromType)
                    {
                        case MUTABLE_FLOAT:
                        case FLOAT:
                        case MUTABLE_DOUBLE:
                        case DOUBLE:
                            setObject(parent, field, child);
                            break;
                        case MUTABLE_INT:
                            setObject(parent, field, ((Integer) child).floatValue());
                            break;
                        case INT:
                            setObject(parent, field, new Integer((int) child).floatValue());
                            break;
                        case MUTABLE_LONG:
                            setObject(parent, field, ((Long) child).floatValue());
                            break;
                        case LONG:
                            setObject(parent, field, new Long((long) child).floatValue());
                            break;
                        case NULL:
                            setObject(parent, field, null);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case MUTABLE_CHAR:
                    switch (fromType)
                    {
                        case MUTABLE_CHAR:
                        case CHAR:
                            setObject(parent, field, child);
                            break;
                        case NULL:
                            setObject(parent, field, null);
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                            break;
                    }
                    break;
                case CHAR:
                    switch (fromType)
                    {
                        case MUTABLE_CHAR:
                        case CHAR:
                            setChar(parent, field, (char)child);
                            break;
                        default:
                            setChar(parent, field, (char)toClass.cast(child));
                            break;
                    }
                    break;
                case STRING:
                    switch (fromType) {
                        case STRING:
                            setObject(parent, field, child.toString());
                            break;
                        default:
                            setObject(parent, field, toClass.cast(child));
                    }
                    break;
                default:

                    if(toType.isArray())
                    {
                        int arrayLength = Array.getLength(child);
                        Object newArray = Array.newInstance(toClass, arrayLength);
                        //noinspection SuspiciousSystemArraycopy
                        System.arraycopy(child, 0, newArray, 0, arrayLength);
                        setObject(parent, field, newArray);
                    }
                    else {
                        setObject(parent, field, toClass.cast(child));
                    }
            }
        } catch (IllegalAccessException ignore) {
            // This is suppressed
        }
    }

    /**
     * Returns a copy of the object, or null if the object cannot
     * be serialized.
     */
    public static void copy(IManagedEntity orig, IManagedEntity dest, EntityDescriptor descriptor) {
        // Copy all attributes
        for (AttributeDescriptor attribute : descriptor.getAttributes().values()) {
            OffsetField field;
            try {
                field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
            } catch (Exception e) {
                try {
                    field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                    ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
                } catch (Exception ignore) {
                }
            }
        }

        for(RelationshipDescriptor attribute : descriptor.getRelationships().values())
        {
            OffsetField field;
            try {
                field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
            } catch (Exception e) {
                try {
                    field = ReflectionUtil.getOffsetField(orig.getClass(), attribute.getName());
                    ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, field), field);
                } catch (Exception ignore) {
                }
            }
        }
    }
}
