package com.onyx.util

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.AttributeTypeMismatchException
import com.onyx.exception.InvalidConstructorException
import com.onyx.extension.common.castTo
import com.onyx.extension.common.catchAll
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*

import java.lang.reflect.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

/**
 * Created by tosborn1 on 8/2/16.
 *
 *
 * The purpose of this class is to encapsulate how we are performing reflection.  The default way is to use the unsafe api
 * but if it is not available, we default to generic reflection.
 *
 * @since 1.2.2 This class has been refactored to remove theUnsafe.  That is unavailable and it did not prooove to provide
 * performance benefit.
 */
object ReflectionUtil {

    // region Fields

    // Cache of class' fields
    private val classFields = ConcurrentHashMap<Class<*>, List<ReflectionField>>(HashMap<Class<*>, List<ReflectionField>>())

    /**
     * Get all the fields to serialize
     *
     * @param object The value to read the fields from
     * @return Fields with their offsets
     */
    fun getFields(`object`: Any): List<ReflectionField> {
        val clazz = `object`.javaClass
        val isManagedEntity = `object`.javaClass.isAnnotationPresent(Entity::class.java)

        return classFields.computeIfAbsent(clazz) { jClass ->
            val fields = ArrayList<ReflectionField>()
            var aClass = jClass
            while (aClass != Any::class.java
                    && aClass != Exception::class.java
                    && aClass != Throwable::class.java) {
                aClass.declaredFields
                        .asSequence()
                        .filter { it.modifiers and Modifier.STATIC == 0 && !Modifier.isTransient(it.modifiers) && it.type != Exception::class.java && it.type != Throwable::class.java }
                        .forEach {
                            if (!isManagedEntity) {
                                fields.add(ReflectionField(it.name, it))
                            } else if (it.isAnnotationPresent(Attribute::class.java)
                                    || it.isAnnotationPresent(Index::class.java)
                                    || it.isAnnotationPresent(Partition::class.java)
                                    || it.isAnnotationPresent(Identifier::class.java)
                                    || it.isAnnotationPresent(Relationship::class.java)) {
                                fields.add(ReflectionField(it.name, it))
                            }
                        }
                aClass = aClass.superclass
            }

            fields.sortBy { it.name }
            fields
        }
    }

    /**
     * Helper for getting the offset field which is a wrapper so that it can be used by the Unsafe for reflection
     *
     * @param field Reflect field to wrap
     * @return The offset field
     */
    fun getReflectionField(field: Field): ReflectionField = ReflectionField(field.name, field)

    /**
     * Helper for getting field that may be an inherited field
     *
     * @param jClass     Parent class to reflect upon
     * @param attribute Attribute to get field of
     * @return The Field that corresponds to that attribute name
     * @throws AttributeMissingException Exception thrown when the field is not there
     */
    @Throws(AttributeMissingException::class)
    fun getField(jClass: Class<*>, attribute: String): Field {
        var clazz = jClass
        while (clazz != Any::class.java) {
            try {
                val f = clazz.getDeclaredField(attribute)
                if (f != null) {
                    f.isAccessible = true
                    return f
                }
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }

        }

        throw AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE)
    }

    // endregion

    // region Construction

    /**
     * Instantiate an instance with a class type
     * Note: If using the unsafe API, this will bypass the constructor
     *
     * @param type the type of class to instantiate
     * @return the fully instantiated value
     * @throws InstantiationException Exception thrown when using unsafe to allocate an instance
     * @throws IllegalAccessException Exception thrown when using regular reflection
     */
    @Throws(InstantiationException::class, IllegalAccessException::class)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> instantiate(type: Class<*>): T {
        try {
            return type.getDeclaredConstructor().newInstance() as T
        } catch (e: InstantiationException) {
            val constructor = type.constructors[0]
            val parameters = constructor.parameters
            val parameterValues = arrayOfNulls<Any>(parameters.size)
            for ((i, parameter) in parameters.withIndex()) {
                if (!parameter.type.isPrimitive)
                    parameterValues[i] = null
                else
                    parameterValues[i] = parameter.type.getDeclaredConstructor().newInstance()
            }
            constructor.isAccessible = true
            try {
                return constructor.newInstance(*parameterValues) as T
            } catch (e1: InvocationTargetException) {
                throw InstantiationException("Cannot instantiate class " + type.canonicalName)
            }
        } catch (e:Exception)
        {
            throw e
        }
    }

    /**
     * Instantiate a managed entity
     * @param type Type of class
     * @return New Instance
     * @throws InvalidConstructorException Exception occurred while creating new value
     *
     * @since 2.0.0 Moved from Entity Descriptor class since it should not be creating new objects
     */
    @Throws(InvalidConstructorException::class)
    fun <T : IManagedEntity> createNewEntity(type: Class<*>): T {
        val entity: T

        try {
            entity = instantiate(type)
        } catch (e1: Exception) {
            throw InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e1)
        }

        return entity
    }

    // endregion

    // region Copy

    /**
     * Returns a copy of the value, or null if the value cannot
     * be serialized.
     */
    fun copy(orig: IManagedEntity, dest: IManagedEntity, descriptor: EntityDescriptor) {
        descriptor.reflectionFields.values.forEach {
            catchAll {
                ReflectionUtil.setAny(dest, ReflectionUtil.getAny(orig, it), it)
            }
        }
    }

    // endregion

    // region Get Methods

    /**
     * Get Int for an value and a field
     *
     * @param parent      The value to get the int field from
     * @param reflectionField The field to reflect on
     * @return a primitive int
     */
    @Throws(IllegalAccessException::class)
    fun getInt(parent: Any, reflectionField: ReflectionField): Int = reflectionField.field.getInt(parent)

    /**
     * Get byte for an value and a field
     *
     * @param parent      The value to get the byte field from
     * @param reflectionField The field to reflect on
     * @return a primitive byte
     */
    @Throws(IllegalAccessException::class)
    fun getByte(parent: Any, reflectionField: ReflectionField): Byte = reflectionField.field.getByte(parent)

    /**
     * Get long for an value and a field
     *
     * @param parent      The value to get the long field from
     * @param reflectionField The field to reflect on
     * @return a primitive long
     */
    @Throws(IllegalAccessException::class)
    fun getLong(parent: Any, reflectionField: ReflectionField): Long = reflectionField.field.getLong(parent)

    /**
     * Get float for an value and a field
     *
     * @param parent      The value to get the float field from
     * @param reflectionField The field to reflect on
     * @return a primitive float
     */
    @Throws(IllegalAccessException::class)
    fun getFloat(parent: Any, reflectionField: ReflectionField): Float = reflectionField.field.getFloat(parent)

    /**
     * Get double for an value and a field
     *
     * @param parent      The value to get the double field from
     * @param reflectionField The field to reflect on
     * @return a primitive double
     */
    @Throws(IllegalAccessException::class)
    fun getDouble(parent: Any, reflectionField: ReflectionField): Double = reflectionField.field.getDouble(parent)

    /**
     * Get short for an value and a field
     *
     * @param parent      The value to get the boolean field from
     * @param reflectionField The field to reflect on
     * @return a primitive boolean
     */
    @Throws(IllegalAccessException::class)
    fun getBoolean(parent: Any, reflectionField: ReflectionField): Boolean = reflectionField.field.getBoolean(parent)

    /**
     * Get short for an value and a field
     *
     * @param parent      The value to get the short field from
     * @param reflectionField The field to reflect on
     * @return a primitive short
     */
    @Throws(IllegalAccessException::class)
    fun getShort(parent: Any, reflectionField: ReflectionField): Short = reflectionField.field.getShort(parent)

    /**
     * Get char for an value and a field
     *
     * @param parent      The value to get the char field from
     * @param reflectionField The field to reflect on
     * @return a primitive char
     */
    @Throws(IllegalAccessException::class)
    fun getChar(parent: Any, reflectionField: ReflectionField): Char = reflectionField.field.getChar(parent)

    /**
     * Get value for an value and a field
     *
     * @param parent      The value to get the value field from
     * @param reflectionField The field to reflect on
     * @return a mutable value of any kind
     */
    @Throws(IllegalAccessException::class)
    @Suppress("UNCHECKED_CAST")
    fun <T> getObject(parent: Any, reflectionField: ReflectionField): T = reflectionField.field.get(parent) as T

    /**
     * This method is to return any key from a field using reflection.  It
     * can either return a primitive or an value.  Note: If inteneded to get a
     * primitive, I recommend using the other api methods to avoid autoboxing.
     *
     * @param object      Parent value
     * @param reflectionField field to get
     * @return field key
     */
    @Throws(AttributeTypeMismatchException::class)
    @Suppress("UNCHECKED_CAST")
    fun <T> getAny(`object`: Any, reflectionField: ReflectionField): T = try {
        reflectionField.field.get(`object`)
    } catch (e2: Exception) {
        null
    } as T

    // endregion

    // region Set Methods

    /**
     * Put an int on an parent value
     *
     * @param parent      The value to put the int on
     * @param reflectionField The field to reflect on
     * @param value       int key to set
     */
    @Throws(IllegalAccessException::class)
    fun setInt(parent: Any, reflectionField: ReflectionField, value: Int) = reflectionField.field.setInt(parent, value)

    /**
     * Put an long on an parent value
     *
     * @param parent      The value to put the long on
     * @param reflectionField The field to reflect on
     * @param value       long key to set
     */
    @Throws(IllegalAccessException::class)
    fun setLong(parent: Any, reflectionField: ReflectionField, value: Long) = reflectionField.field.setLong(parent, value)

    /**
     * Put an byte on an parent value
     *
     * @param parent      The value to put the byte on
     * @param reflectionField The field to reflect on
     * @param value       byte key to set
     */
    @Throws(IllegalAccessException::class)
    fun setByte(parent: Any, reflectionField: ReflectionField, value: Byte) = reflectionField.field.setByte(parent, value)

    /**
     * Put an float on an parent value
     *
     * @param parent      The value to put the float on
     * @param reflectionField The field to reflect on
     * @param value       float key to set
     */
    @Throws(IllegalAccessException::class)
    fun setFloat(parent: Any, reflectionField: ReflectionField, value: Float) = reflectionField.field.setFloat(parent, value)

    /**
     * Put an double on an parent value
     *
     * @param parent      The value to put the double on
     * @param reflectionField The field to reflect on
     * @param value       double key to set
     */
    @Throws(IllegalAccessException::class)
    fun setDouble(parent: Any, reflectionField: ReflectionField, value: Double) = reflectionField.field.setDouble(parent, value)

    /**
     * Put an short on an parent value
     *
     * @param parent      The value to put the short on
     * @param reflectionField The field to reflect on
     * @param value       short key to set
     */
    @Throws(IllegalAccessException::class)
    fun setShort(parent: Any, reflectionField: ReflectionField, value: Short) = reflectionField.field.setShort(parent, value)

    /**
     * Put an boolean on an parent value
     *
     * @param parent      The value to put the boolean on
     * @param reflectionField The field to reflect on
     * @param value       boolean key to set
     */
    @Throws(IllegalAccessException::class)
    fun setBoolean(parent: Any, reflectionField: ReflectionField, value: Boolean) = reflectionField.field.setBoolean(parent, value)

    /**
     * Put an char on an parent value
     *
     * @param parent      The value to put the char on
     * @param reflectionField The field to reflect on
     * @param value       char key to set
     */
    @Throws(IllegalAccessException::class)
    fun setChar(parent: Any, reflectionField: ReflectionField, value: Char) = reflectionField.field.setChar(parent, value)

    /**
     * Put an value on an parent value
     *
     * @param parent      The value to put the value on
     * @param reflectionField The field to reflect on
     * @param value       value key to set
     */
    @Throws(IllegalAccessException::class)
    fun setObject(parent: Any, reflectionField: ReflectionField, value: Any?) = reflectionField.field.set(parent, value)

    /**
     * Reflection utility for setting an attribute
     *
     * @param parent Parent value to set property on
     * @param child  Child value that is the property value
     * @param field  Field to set on the parent
     */
    @Throws(AttributeMissingException::class)
    fun setAny(parent: Any, child: Any?, field: ReflectionField) {
        var propertyValue = child

        if (propertyValue != null && !field.type.isAssignableFrom(propertyValue.javaClass))
            propertyValue = propertyValue.castTo(field.type)

        catchAll {
            field.field.set(parent, propertyValue)
        }
    }

    // endregion

}
