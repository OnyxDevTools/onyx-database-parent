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
 * @return The type casted object
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
            Char::class -> this.toChar()
            Byte::class -> this.toByte()
            Short::class -> this.toShort()
            String::class -> this.toString()
            Date::class -> Date(this.toLong())
            else -> null
        }
        clazz == String::class -> return this.toString()
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
            Int::class -> this.toInt()
            Long::class -> this.toLong()
            Double::class -> this.toDouble()
            Float::class -> this.toFloat()
            Boolean::class -> this.toInt() != 0
            Char::class -> this.chars().findFirst()
            Byte::class -> this.toByte()
            Short::class -> this.toShort()
            Date::class -> Date(this.toLong())
            else -> null
        }
        this is Date -> return when (kotlinClass) {
            Long::class -> this.time
            else -> null
        }
        else -> null
    }
}