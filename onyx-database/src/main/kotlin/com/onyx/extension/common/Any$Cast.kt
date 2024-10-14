package com.onyx.extension.common

import java.util.*
import kotlin.reflect.KClass

/**
 * Cast to will take the value passed in and attempt to cast it
 * as the class passed in.  This is enabled to support java types
 * but, it will use the kotlin class since it does not have to take into
 * account primitives.
 *
 * @param clazz Class to cast to
 *
 * @return The type cast object
 */
fun Any?.castTo(clazz: Class<*>): Any? {

    val kotlinClass: KClass<*> = clazz.kotlin

    if((this != null && clazz === this::class.java)
            || (this != null && clazz === this::class.javaPrimitiveType))
        return this

    return when {
        // Cast to numeric value when value is null
        this == null -> when(kotlinClass) {
            Int::class -> 0
            Long::class -> 0L
            Double::class -> 0.0
            Float::class -> 0f
            Boolean::class -> false
            Char::class -> '0'
            Byte::class -> 0.toByte()
            Short::class -> 0.toShort()
            else -> null
        }
        // When value is number
        this is Number -> when (kotlinClass) {
            Int::class -> this.toInt()
            Long::class -> this.toLong()
            Double::class -> this.toDouble()
            Float::class -> this.toFloat()
            Boolean::class -> this.toInt() != 0
            Char::class -> this.toInt().toChar()
            Byte::class -> this.toByte()
            Short::class -> this.toShort()
            String::class -> this.toString()
            Date::class -> Date(this.toLong())
            else -> null
        }
        clazz == String::class.java -> return this.toString()
        this is Boolean -> return when (kotlinClass) {
            Date::class -> null
            Int::class -> if (this) 1 else 0
            Long::class -> if (this) 1L else 0L
            Double::class -> if (this) 1.0 else 0.0
            Float::class -> if (this) 1f else 0f
            Boolean::class -> this
            Char::class -> if (this) '1' else '0'
            Byte::class -> if (this) 1.toByte() else 0.toByte()
            Short::class -> if (this) 1.toShort() else 0.toShort()
            else -> null
        }
        this is String -> return when (kotlinClass) {
            Int::class -> this.toIntOrNull() ?: 0
            Long::class -> this.toLongOrNull() ?: 0L
            Double::class -> this.toDoubleOrNull() ?: 0.0
            Float::class -> this.toFloatOrNull() ?: 0.0f
            Boolean::class -> (this.toIntOrNull() ?: 0) != 0
            Char::class -> this.chars().findFirst()
            Byte::class -> this.toByteOrNull() ?: 0
            Short::class -> this.toShortOrNull() ?: 0
            Date::class -> Date(this.toLongOrNull() ?: 0L)
            else -> null
        }
        this is Date -> return when (kotlinClass) {
            Long::class -> this.time
            else -> null
        }
        else -> null
    }
}

/**
 * This method will convert any other primitive value to a long
 */
fun Any.long():Long =
    when {
        this::class.java == ClassMetadata.FLOAT_TYPE ->             java.lang.Float.floatToIntBits(this as Float).toLong()
        this::class.java == ClassMetadata.FLOAT_PRIMITIVE_TYPE ->   java.lang.Float.floatToIntBits(this as Float).toLong()
        this::class.java == ClassMetadata.DOUBLE_TYPE ->            java.lang.Double.doubleToLongBits(this as Double)
        this::class.java == ClassMetadata.DOUBLE_PRIMITIVE_TYPE ->  java.lang.Double.doubleToLongBits(this as Double)
        this is Number -> this.toLong()
        this is Boolean -> if(this) 1L else 0L
        this is Char -> this.code.toLong()
        this === "" -> 0L
        else -> throw Exception("Invalid Cast ${this::class.java}")
    }

/**
 * This function will convert a long to any other primitive type
 */
@Suppress("UNCHECKED_CAST")
fun Long.toType(type:Class<*>):Any = when (type) {
    ClassMetadata.LONG_TYPE ->              this
    ClassMetadata.LONG_PRIMITIVE_TYPE ->    this
    ClassMetadata.INT_TYPE ->               this.toInt()
    ClassMetadata.INT_PRIMITIVE_TYPE ->     this.toInt()
    ClassMetadata.FLOAT_TYPE ->             java.lang.Float.intBitsToFloat(this.toInt())
    ClassMetadata.FLOAT_PRIMITIVE_TYPE ->   java.lang.Float.intBitsToFloat(this.toInt())
    ClassMetadata.DOUBLE_TYPE ->            java.lang.Double.longBitsToDouble(this)
    ClassMetadata.DOUBLE_PRIMITIVE_TYPE ->  java.lang.Double.longBitsToDouble(this)
    ClassMetadata.BYTE_TYPE ->              this.toByte()
    ClassMetadata.BYTE_PRIMITIVE_TYPE ->    this.toByte()
    ClassMetadata.CHAR_TYPE ->              this.toInt().toChar()
    ClassMetadata.CHAR_PRIMITIVE_TYPE ->    this.toInt().toChar()
    ClassMetadata.SHORT_TYPE ->             this.toShort()
    ClassMetadata.SHORT_PRIMITIVE_TYPE ->   this.toShort()
    ClassMetadata.BOOLEAN_TYPE ->           this == 1L
    ClassMetadata.BOOLEAN_PRIMITIVE_TYPE -> this == 1L
    else -> throw Exception("Invalid Cast $type")
}