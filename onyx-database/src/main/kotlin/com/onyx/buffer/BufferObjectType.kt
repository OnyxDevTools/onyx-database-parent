package com.onyx.buffer

import com.onyx.extension.common.ClassMetadata
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import java.util.Date
import kotlin.Pair

/**
 * This Enum indicates all of the different types of objects that can be serialized
 */
enum class BufferObjectType constructor(private val type: Class<*>?) {

    NULL(null),
    REFERENCE(null),
    ENUM(Enum::class.java),

    // Primitives
    BYTE(Byte::class.javaPrimitiveType),
    INT(Int::class.javaPrimitiveType),
    LONG(Long::class.javaPrimitiveType),
    SHORT(Short::class.javaPrimitiveType),
    FLOAT(Float::class.javaPrimitiveType),
    DOUBLE(Double::class.javaPrimitiveType),
    BOOLEAN(Boolean::class.javaPrimitiveType),
    CHAR(Char::class.javaPrimitiveType),

    // Primitive Arrays
    BYTE_ARRAY(ByteArray::class.java),
    INT_ARRAY(IntArray::class.java),
    LONG_ARRAY(LongArray::class.java),
    SHORT_ARRAY(ShortArray::class.java),
    FLOAT_ARRAY(FloatArray::class.java),
    DOUBLE_ARRAY(DoubleArray::class.java),
    BOOLEAN_ARRAY(BooleanArray::class.java),
    CHAR_ARRAY(CharArray::class.java),
    OBJECT_ARRAY(Array<Any>::class.java),
    OTHER_ARRAY(Array<Any>::class.java),

    // Mutable
    MUTABLE_BYTE(Byte::class.javaObjectType),
    MUTABLE_INT(Int::class.javaObjectType),
    MUTABLE_LONG(Long::class.javaObjectType),
    MUTABLE_SHORT(Short::class.javaObjectType),
    MUTABLE_FLOAT(Float::class.javaObjectType),
    MUTABLE_DOUBLE(Double::class.javaObjectType),
    MUTABLE_BOOLEAN(Boolean::class.javaObjectType),
    MUTABLE_CHAR(Char::class.javaObjectType),

    // Object Serializable
    BUFFERED(BufferStreamable::class.java),

    // Objects
    DATE(Date::class.java),
    STRING(String::class.java),
    CLASS(Class::class.java),
    COLLECTION(Collection::class.java),
    MAP(Map::class.java),

    ENTITY(IManagedEntity::class.java),

    OTHER(null),
    PAIR(Pair::class.java);

    companion object {

        /**
         * Get Object type for the class
         *
         * @param `value` Object in Question
         * @return The serializer type that correlates to that class.
         */
        fun getTypeCodeForClass(value: Any?, context: SchemaContext?): BufferObjectType {

            if (value == null)
                return NULL

            val type = value.javaClass

            if(context != null && ClassMetadata.MANAGED_ENTITY.isAssignableFrom(type))
                return ENTITY

            if (type.isEnum)
                return ENUM

            else if (value is IManagedEntity && context == null)
                return OTHER

            return enumValues
                    .firstOrNull { it.type != null && it.type.isAssignableFrom(type) }
                    ?.let {
                        if (it === OBJECT_ARRAY && type != Array<Any>::class.java) {
                            OTHER_ARRAY
                        } else it
                    }
                    ?: OTHER
        }

        // Java in all of its wisdom clones the array each time you invoke values.
        // Surprisingly this is expensive so, this is a way around that.
        val enumValues = values()
    }
}
