package com.onyx.cloud.extensions

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

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

object ReflectionCache {
    private val cache = mutableMapOf<KClass<*>, Map<String, KCallable<*>>>()

    fun getCachedMembers(kClass: KClass<*>): Map<String, KCallable<*>> {
        return cache.getOrPut(kClass) {
            val properties = kClass.memberProperties.associateBy { it.name }
            val getters = kClass.memberFunctions.filter { it.parameters.size == 1 }.associateBy { it.name }
            properties + getters
        }
    }
}
