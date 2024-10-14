package com.onyx.lang.map

import java.util.LinkedHashMap

open class LastRecentlyUsedMap<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize

    open fun forEach(action: (K,V?) -> Unit) {
        entries.forEach { action.invoke(it.key, it.value) }
    }
}
