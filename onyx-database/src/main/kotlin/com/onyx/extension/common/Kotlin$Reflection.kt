package com.onyx.extension.common

import kotlin.reflect.*
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.memberFunctions

object ReflectionCache {
    private val cache = mutableMapOf<KClass<*>, Map<String, KCallable<*>>>()

    fun getCachedMembers(kClass: KClass<*>): Map<String, KCallable<*>> {
        return cache.getOrPut(kClass) {
            val properties = kClass.memberProperties.associateBy { it.name }
            val getters = kClass.memberFunctions.filter { it.parameters.size == 1 }.associateBy { it.name }
            properties + getters
        }
    }

    fun hasMember(kClass: KClass<*>, name: String): Boolean = getCachedMembers(kClass).containsKey(name)
}

/**
 * Checks if a given [propertyPath] could be resolved on this [KClass] (by property or zero-arg function)
 * following the same structure as the [get] extension. Map keys are treated as automatically valid
 * once you hit a Map type.
 */
fun KClass<*>.hasKey(propertyPath: String): Boolean {
    val segments = propertyPath.split(".")
    var currentClass: KClass<*> = this

    for (segment in segments) {
        // If we're already at a Map, assume any further key is valid.
        if (currentClass.isSubclassOf(Map::class)) {
            return true
        }

        if (currentClass.isSubclassOf(List::class)) {
            return true
        }

        val cachedMembers = ReflectionCache.getCachedMembers(currentClass)
        when (val member = cachedMembers[segment]) {
            is KProperty1<*, *> -> {
                val returnType = member.returnType.classifier as? KClass<*>
                    ?: return false
                currentClass = returnType
            }
            is KFunction<*> -> {
                val returnType = member.returnType.classifier as? KClass<*>
                    ?: return false
                currentClass = returnType
            }
            else -> {
                // Not a property, not a function, doesn't exist:
                return false
            }
        }
    }

    return true
}

@Suppress("UNCHECKED_CAST")
fun <T> Any?.get(propertyPath: String): T? {
    if (this == null) return null

    val properties = propertyPath.split(".")
    var currentObject: Any? = this

    for (property in properties) {
        currentObject = when (currentObject) {
            is Map<*, *> -> currentObject[property]
            is List<*> -> {
                if(property.toIntOrNull() != null){
                    currentObject.getOrNull(property.toInt())
                } else if (property == "any") {
                    // Handle list mapping here when encountering "any"
                    val remainingPath = properties.subList(properties.indexOf(property) + 1, properties.size).joinToString(".")
                    if (remainingPath.isEmpty()) {
                        return currentObject as T
                    }
                    return currentObject.map { it.get<T>(remainingPath) } as? T
                } else {
                    return null
                }
            }
            else -> {
                currentObject?.let {
                    val kClass = it::class
                    val cachedMembers = ReflectionCache.getCachedMembers(kClass)
                    @Suppress("UNCHECKED_CAST")
                    when (val member = cachedMembers[property]) {
                        is KProperty1<*, *> -> (member as KProperty1<Any, *>).get(it)
                        is KFunction<*> -> member.call(it)
                        else -> null
                    }
                }
            }
        }

        if (currentObject == null) {
            break
        }
    }

    return currentObject as? T
}

fun <T> Any?.put(propertyPath: String, value: T) {
    if (this == null) throw IllegalArgumentException("Cannot set a property on a null object.")

    val properties = propertyPath.split(".")
    var currentObject: Any? = this
    val propertyCopy = properties.toList()

    for ((index, property) in properties.withIndex()) {
        val currentProperty = propertyCopy.drop(1)
        when (currentObject) {
            is MutableMap<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = currentObject as MutableMap<String, Any?>

                if (index == properties.size - 1) {
                    // At the last property in the path, set the value
                    map[property] = value
                } else {
                    // Traverse or create intermediate map
                    currentObject = map.getOrPut(property) { mutableMapOf<String, Any?>() }
                }
            }
            is List<*> -> {
                currentObject.forEach {
                    it.put(currentProperty.joinToString("."), value)
                }
            }
            else -> {
                val kClass = currentObject?.let { it::class } ?: throw IllegalStateException("Current object is null.")
                val cachedMembers = ReflectionCache.getCachedMembers(kClass)
                val member = cachedMembers[property]

                if (index == properties.size - 1) {
                    // At the last property in the path, set the value
                    when (member) {
                        is KMutableProperty1<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            (member as KMutableProperty1<Any, T>).set(currentObject, value)
                        }
                        else -> throw IllegalArgumentException("Property '$property' is not mutable or does not exist.")
                    }
                } else {
                    // Traverse intermediate property
                    currentObject = when (member) {
                        is KProperty1<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            (member as KProperty1<Any, *>).get(currentObject)
                        }
                        is KFunction<*> -> member.call(currentObject)
                        else -> throw IllegalArgumentException("Property '$property' does not exist.")
                    }

                    if (currentObject == null) {
                        // If an intermediate property is null, create a new map for further traversal
                        if (member is KMutableProperty1<*, *>) {
                            val newMap = mutableMapOf<String, Any?>()
                            @Suppress("UNCHECKED_CAST")
                            (member as KMutableProperty1<Any, Any?>).set(this, newMap)
                            currentObject = newMap
                        } else {
                            throw IllegalStateException("Intermediate property '$property' is null and not mutable.")
                        }
                    }
                }
            }
        }
    }
}
