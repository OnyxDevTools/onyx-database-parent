package com.onyx.extension.common

import com.onyx.interactors.classfinder.ApplicationClassFinder
import com.onyx.persistence.annotations.*
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private val classMetadatas: MutableMap<String, ClassMetadata> = Collections.synchronizedMap(hashMapOf())

fun metadata(contextId: String) = classMetadatas.getOrPut(contextId) { ClassMetadata() }

/**
 * Get fields for a class that apply to its reflection and serialization.  All transient fields and or fields
 * that do not apply to an entity persistence if it is a managed entity will be excluded.
 *
 * @since 2.0.0
 */
fun Any.getFields(contextId: String) : List<Field> = metadata(contextId).fields(this.javaClass)

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
fun <T : Any> Class<*>.instance(contextId: String): T {
    val metadata = metadata(contextId)
    try {
        return metadata.constructor(this).newInstance() as T
    } catch (e: InstantiationException) {
        val constructor = this.constructors[0]
        constructor.isAccessible = true
        val parameters = constructor.parameters
        val parameterValues = arrayOfNulls<Any>(parameters.size)
        parameters.forEachIndexed { index, parameter ->
            parameterValues[index] = if(parameter.type.isPrimitive) metadata.constructor(parameter.type).newInstance() else null
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
fun Any.copy(contextId: String, from: Any) = from.getFields(contextId).forEach {
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
        child != null && (field.type === child.javaClass || field.type === child.javaClass.primitiveType()) -> field.set(this, child)
        child != null && !field.type.isAssignableFrom(child.javaClass) -> field.set(this, child.castTo(field.type))
        else -> field.set(this, child)
    }
}

fun Class<*>.canBeCastToPrimitive():Boolean = when (this) {
    ClassMetadata.LONG_TYPE ->              true
    ClassMetadata.LONG_PRIMITIVE_TYPE ->    true
    ClassMetadata.INT_TYPE ->               true
    ClassMetadata.INT_PRIMITIVE_TYPE ->     true
    ClassMetadata.FLOAT_TYPE ->             true
    ClassMetadata.FLOAT_PRIMITIVE_TYPE ->   true
    ClassMetadata.DOUBLE_TYPE ->            true
    ClassMetadata.DOUBLE_PRIMITIVE_TYPE ->  true
    ClassMetadata.BYTE_TYPE ->              true
    ClassMetadata.BYTE_PRIMITIVE_TYPE ->    true
    ClassMetadata.CHAR_TYPE ->              true
    ClassMetadata.CHAR_PRIMITIVE_TYPE ->    true
    ClassMetadata.SHORT_TYPE ->             true
    ClassMetadata.SHORT_PRIMITIVE_TYPE ->   true
    ClassMetadata.BOOLEAN_TYPE ->           true
    ClassMetadata.BOOLEAN_PRIMITIVE_TYPE -> true
    else -> false
}

fun Class<*>.primitiveType() = when {
    this.isPrimitive -> this
    this.name == "java.lang.Boolean" -> ClassMetadata.BOOLEAN_TYPE
    this.name == "java.lang.Character" -> ClassMetadata.CHAR_TYPE
    this.name == "java.lang.Byte" -> ClassMetadata.BYTE_TYPE
    this.name == "java.lang.Short" -> ClassMetadata.SHORT_TYPE
    this.name == "java.lang.Integer" -> ClassMetadata.INT_TYPE
    this.name == "java.lang.Float" -> ClassMetadata.FLOAT_TYPE
    this.name == "java.lang.Long" -> ClassMetadata.LONG_TYPE
    this.name == "java.lang.Double" -> ClassMetadata.DOUBLE_TYPE
    else -> null
}

// endregion

/**
 * This object is a container of cached class information.  Since some of these lookups are slow, this
 * will use optimistic locking caching to speed the lookup up.
 */
class ClassMetadata {

    companion object {
        val STRING_TYPE = String::class.java
        val BOOLEAN_TYPE = Boolean::class.javaObjectType
        val CHAR_TYPE = Char::class.javaObjectType
        val BYTE_TYPE = Byte::class.javaObjectType
        val SHORT_TYPE = Short::class.javaObjectType
        val INT_TYPE = Int::class.javaObjectType
        val FLOAT_TYPE = Float::class.javaObjectType
        val LONG_TYPE = Long::class.javaObjectType
        val DOUBLE_TYPE = Double::class.javaObjectType
        val LONG_PRIMITIVE_TYPE = Long::class.javaPrimitiveType!!
        val INT_PRIMITIVE_TYPE = Int::class.javaPrimitiveType!!
        val DOUBLE_PRIMITIVE_TYPE = Double::class.javaPrimitiveType!!
        val FLOAT_PRIMITIVE_TYPE = Float::class.javaPrimitiveType!!
        val BYTE_PRIMITIVE_TYPE = Byte::class.javaPrimitiveType!!
        val CHAR_PRIMITIVE_TYPE = Char::class.javaPrimitiveType!!
        val SHORT_PRIMITIVE_TYPE = Short::class.javaPrimitiveType!!
        val BOOLEAN_PRIMITIVE_TYPE = Boolean::class.javaPrimitiveType!!
        val BYTE_ARRAY = ByteArray::class.java
        val INT_ARRAY = IntArray::class.java
        val LONG_ARRAY = LongArray::class.java
        val FLOAT_ARRAY = FloatArray::class.java
        val DOUBLE_ARRAY = DoubleArray::class.java
        val BOOLEAN_ARRAY = BooleanArray::class.java
        val CHAR_ARRAY = CharArray::class.java
        val SHORT_ARRAY = ShortArray::class.java
        val ATTRIBUTE_ANNOTATION = Attribute::class.java
        val PARTITION_ANNOTATION = Partition::class.java
        val INDEX_ANNOTATION = Index::class.java
        val IDENTIFIER_ANNOTATION = Identifier::class.java
        val RELATIONSHIP_ANNOTATION = Relationship::class.java
        val ENTITY_ANNOTATION = Entity::class.java
        val ANY_CLASS = Any::class.java
        val MANAGED_ENTITY = IManagedEntity::class.java

        fun purge(contextId: String) {
            classMetadatas.remove(contextId)
        }
    }

    private val constructors = OptimisticLockingMap<Class<*>, Constructor<*>>(HashMap())
    private val classes = OptimisticLockingMap<String, Class<*>>(HashMap())
    private val classFields = OptimisticLockingMap<Class<*>, List<Field>>(HashMap())

    fun removeClass(name: String) {
        classes.remove(name).apply {
            constructors.remove(this)
            classFields.remove(this)
        }
    }

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
    fun classForName(name:String, schemaContext: SchemaContext? = null): Class<*> = classes.getOrPut(name) { ApplicationClassFinder.forName(name, schemaContext) }

    /**
     * Get fields for a java class
     *
     * @since 2.0.0
     */
    fun fields(clazz:Class<*>) : List<Field> {
        val isManagedEntity = clazz.isAnnotationPresent(ENTITY_ANNOTATION)

        return classFields.getOrPut(clazz) {
            val fields = ArrayList<Field>()
            var aClass:Class<*> = clazz
            while (aClass != ANY_CLASS
                    && aClass != Exception::class.java
                    && aClass != Throwable::class.java) {
                aClass.declaredFields
                        .asSequence()
                        .filter { it.modifiers and Modifier.STATIC == 0 && !Modifier.isTransient(it.modifiers) && it.type != Exception::class.java && it.type != Throwable::class.java }
                        .forEach {
                            if (!isManagedEntity) {
                                it.isAccessible = true
                                fields.add(it)
                            } else if (it.isAnnotationPresent(ATTRIBUTE_ANNOTATION)
                                    || it.isAnnotationPresent(INDEX_ANNOTATION)
                                    || it.isAnnotationPresent(PARTITION_ANNOTATION)
                                    || it.isAnnotationPresent(IDENTIFIER_ANNOTATION)
                                    || it.isAnnotationPresent(RELATIONSHIP_ANNOTATION)) {
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