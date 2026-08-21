package com.onyx.diskmap

/**
 * A map entry with the stable on-disk ID used by database references.
 *
 * This keeps callers independent of the map's indexing data structure.
 */
interface DiskMapEntry<K, V> : MutableMap.MutableEntry<K, V> {
    val recordId: Long
}
