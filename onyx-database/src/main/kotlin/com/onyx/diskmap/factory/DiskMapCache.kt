package com.onyx.diskmap.factory

import java.util.concurrent.TimeUnit

/**
 * @author Tim Osborn
 *
 * This class helps reduce the amount of open files a database can have. If a file is not accessed within a minute,
 * it is eligible to be evicted from the cache.  In the event the use chooses memory mapped storage, this will prevent
 * a large growing memory footprint and therefore improve performance
 */
open class DiskMapCache<K> : MutableMap<K, DiskMapFactory> {
    private val internalMap = object : LinkedHashMap<K, DiskMapEntry>(200, .75f, true) {
        override fun removeEldestEntry(entry: MutableMap.MutableEntry<K, DiskMapEntry>?): Boolean {
            val remove = ((entry!!.value.accessed + TimeUnit.MINUTES.toMillis(1)) < System.currentTimeMillis())
            if(remove) {
                entry.value.diskMapFactory.commit()
                entry.value.diskMapFactory.close()
            }
            return remove
        }

        override fun get(key: K): DiskMapEntry? {
            val value = super.get(key)
            value?.let {
                it.accessed = System.currentTimeMillis()
            }
            return value
        }
    }

    @Synchronized
    override fun get(key: K): DiskMapFactory? = internalMap[key]?.diskMapFactory

    @Synchronized
    override fun put(key: K, value: DiskMapFactory): DiskMapFactory {
        internalMap[key] = DiskMapEntry(System.currentTimeMillis(), value)
        return value
    }

    override val size: Int
        get() = internalMap.size

    override fun containsKey(key: K): Boolean = internalMap.containsKey(key)

    override fun containsValue(value: DiskMapFactory): Boolean = internalMap.values.any { it.diskMapFactory === value }

    override fun isEmpty(): Boolean = internalMap.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<K, DiskMapFactory>>
        get() = internalMap.entries.map { entry -> object : MutableMap.MutableEntry<K, DiskMapFactory> {
                override val key: K
                    get() = entry.key
                override val value: DiskMapFactory
                    get() = entry.value.diskMapFactory
                override fun setValue(newValue: DiskMapFactory): DiskMapFactory = newValue
            }
        }.toMutableSet()

    override val keys: MutableSet<K>
        get() = internalMap.keys

    override val values: MutableCollection<DiskMapFactory>
        get() = internalMap.values.map { it.diskMapFactory }.toMutableList()

    override fun clear() = internalMap.clear()

    override fun putAll(from: Map<out K, DiskMapFactory>) {
        from.forEach { put(it.key, it.value) }
    }

    override fun remove(key: K): DiskMapFactory? = internalMap.remove(key)?.diskMapFactory
}

class DiskMapEntry(var accessed: Long, val diskMapFactory: DiskMapFactory)
