package com.onyx.extension.common

import com.onyx.persistence.annotations.*
import com.onyx.lang.map.OptimisticLockingMap
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.ArrayList

/**
 * Get fields for a class that apply to its reflection and serialization.  All transient fields and or fields
 * that do not apply to an entity persistence if it is a managed entity will be excluded.
 *
 * @since 2.0.0
 */
fun Any.getFields() : List<Field> = ClassMetadata.fields(this.javaClass)

/**
 * Instantiate an instance of the class.  As a pre-requisite to invoking this method, there must
 * be a default constructor that is accessible.
 *
 * @return Instantiated value
 * @throws InstantiationException Exception thrown when using unsafe to allocate an instance
 * @throws IllegalAccessException Exception thrown when using regular reflection
 *
 * @since 2.0.0
 */
@Throws(InstantiationException::class, IllegalAccessException::class)
@Suppress("UNCHECKED_CAST")
fun <T : Any> Class<*>.instance(): T {
    try {
        return ClassMetadata.constructor(this).newInstance() as T
    } catch (e: InstantiationException) {
        val constructor = this.constructors[0]
        constructor.isAccessible = true
        val parameters = constructor.parameters
        val parameterValues = arrayOfNulls<Any>(parameters.size)
        parameters.forEachIndexed { index, parameter ->
            parameterValues[index] = if(parameter.type.isPrimitive) ClassMetadata.constructor(parameter.type).newInstance() else null
        }
        return try {
            constructor.newInstance(*parameterValues) as T
        } catch (e1: InvocationTargetException) {
            throw InstantiationException("Cannot instantiate class " + this.canonicalName)
        }
    }
}

/**
 * Copy an entity's properties from another entity of the same type
 *
 * @param from Entity to copy properties from
 * @since 2.0.0
 */
fun Any.copy(from: Any) = from.getFields().forEach {
    catchAll {
        this.setAny(it, from.getAny(it))
    }
}

// region Get Methods

fun Any.getInt(field: Field): Int = field.getInt(this)
fun Any.getByte(field: Field): Byte = field.getByte(this)
fun Any.getLong(field: Field): Long = field.getLong(this)
fun Any.getFloat(field: Field): Float = field.getFloat(this)
fun Any.getDouble(field: Field): Double = field.getDouble(this)
fun Any.getBoolean(field: Field): Boolean = field.getBoolean(this)
fun Any.getShort(field: Field): Short = field.getShort(this)
fun Any.getChar(field: Field): Char = field.getChar(this)
@Suppress("UNCHECKED_CAST")
fun <T> Any.getObject(field: Field): T = field.get(this) as T
@Suppress("UNCHECKED_CAST")
fun <T> Any.getAny(field: Field): T = try { field.get(this) } catch (e2: Exception) { null } as T

// endregion

// region Set Methods

fun Any.setInt(field: Field, value: Int) = field.setInt(this, value)
fun Any.setLong(field: Field, value: Long) = field.setLong(this, value)
fun Any.setByte(field: Field, value: Byte) = field.setByte(this, value)
fun Any.setFloat(field: Field, value: Float) = field.setFloat(this, value)
fun Any.setDouble(field: Field, value: Double) = field.setDouble(this, value)
fun Any.setShort(field: Field, value: Short) = field.setShort(this, value)
fun Any.setBoolean(field: Field, value: Boolean) = field.setBoolean(this, value)
fun Any.setChar(field: Field, value: Char) = field.setChar(this, value)
fun Any.setObject(field: Field, value: Any?) = field.set(this, value)
fun Any.setAny(field: Field, child: Any?) = catchAll {
    when {
        child != null && field.type === child::class.javaPrimitiveType -> field.set(this, child)
        child != null && !field.type.isAssignableFrom(child.javaClass) -> field.set(this, child.castTo(field.type))
        else -> field.set(this, child)
    }
}

// endregion

/**
 * This object is a container of cached class information.  Since some of these lookups are slow, this
 * will use optimistic locking caching to speed the lookup up.
 */
object ClassMetadata {

    private val constructors = OptimisticLockingMap<Class<*>, Constructor<*>>(HashMap())
    private val classes = OptimisticLockingMap<String, Class<*>>(HashMap())
    private val classFields = OptimisticLockingMap<Class<*>, List<Field>>(HashMap())

    // region Get Reflection Information

    /**
     * Get A constructor for a java class
     *
     * @since 2.0.0
     */
    fun constructor(clazz: Class<*>): Constructor<*> = constructors.getOrPut(clazz) {
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        return@getOrPut constructor
    }

    /**
     * Get a class by its simple name
     *
     * @since 2.0.0
     */
    fun classForName(name:String) = classes.getOrPut(name) { Class.forName(name) }

    /**
     * Get fields for a java class
     *
     * @since 2.0.0
     */
    fun fields(clazz:Class<*>) : List<Field> {
        val isManagedEntity = clazz.isAnnotationPresent(Entity::class.java)

        return classFields.getOrPut(clazz) {
            val fields = ArrayList<Field>()
            var aClass:Class<*> = clazz
            while (aClass != Any::class.java
                    && aClass != Exception::class.java
                    && aClass != Throwable::class.java) {
                aClass.declaredFields
                        .asSequence()
                        .filter { it.modifiers and Modifier.STATIC == 0 && !Modifier.isTransient(it.modifiers) && it.type != Exception::class.java && it.type != Throwable::class.java }
                        .forEach {
                            if (!isManagedEntity) {
                                it.isAccessible = true
                                fields.add(it)
                            } else if (it.isAnnotationPresent(Attribute::class.java)
                                    || it.isAnnotationPresent(Index::class.java)
                                    || it.isAnnotationPresent(Partition::class.java)
                                    || it.isAnnotationPresent(Identifier::class.java)
                                    || it.isAnnotationPresent(Relationship::class.java)) {
                                it.isAccessible = true
                                fields.add(it)
                            }
                        }
                aClass = aClass.superclass
            }

            fields.sortBy { it.name }
            fields
        }
    }

    // endregion
}