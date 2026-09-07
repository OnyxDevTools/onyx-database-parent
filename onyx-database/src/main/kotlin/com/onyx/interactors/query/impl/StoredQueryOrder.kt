package com.onyx.interactors.query.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.extension.common.ReflectionCache
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

/** QuerySortComparator invokes getters, which may transform a persisted sort key. */
internal fun EntityDescriptor.hasStoredAttributeOrder(attribute: String): Boolean {
    val field = attributes[attribute]?.field ?: return false
    val member = ReflectionCache.getCachedMembers(entityClass.kotlin)[attribute] ?: return true
    if (member !is KProperty1<*, *> || member.javaField != field) return false

    // Kotlin reflection exposes default-accessor metadata only through its implementation.
    // If the metadata is unavailable, retain the ordinary comparison path.
    return runCatching {
        val getter = member.getter
        val accessor = getter.javaClass.getMethod("getDescriptor").invoke(getter)
        accessor.javaClass.getMethod("isDefault").invoke(accessor) == true
    }.getOrDefault(false)
}
