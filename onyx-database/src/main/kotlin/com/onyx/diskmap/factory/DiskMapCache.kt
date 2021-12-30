package com.onyx.diskmap.factory

import java.util.concurrent.TimeUnit

/**
 * @author Tim Osborn
 *
 * This class helps reduce the amount of open files a database can have. If a file is not accessed within a minute,
 * it is eligible to be evicted from the cache.  In the event the use chooses memory mapped storage, this will prevent
 * a large growing memory footprint and therefore improve performance
 */
open class DiskMapCache : MutableMap<String, DiskMapFactory> {
    private val internalMap = object : LinkedHashMap<String, DiskMapEntry>(200, .75f, true) {
        override fun removeEldestEntry(entry: MutableMap.MutableEntry<String, DiskMapEntry>?): Boolean {
            val remove = ((entry!!.value.accessed + TimeUnit.MINUTES.toMillis(1)) < System.currentTimeMillis())
            if(remove) {
                entry.value.diskMapFactory.commit()
                entry.value.diskMapFactory.close()
            }
            return remove
        }

        override fun get(key: String): DiskMapEntry? {
            val value = super.get(key)
            value?.let {
                it.accessed = System.currentTimeMillis()
            }
            return value
        }
    }

    override fun get(key: String): DiskMapFactory? = internalMap[key]?.diskMapFactory

    override fun put(key: String, value: DiskMapFactory): DiskMapFactory {
        internalMap[key] = DiskMapEntry(System.currentTimeMillis(), value)
        return value
    }

    override val size: Int
        get() = internalMap.size

    override fun containsKey(key: String): Boolean = internalMap.containsKey(key)

    override fun containsValue(value: DiskMapFactory): Boolean = internalMap.values.any { it.diskMapFactory === value }

    override fun isEmpty(): Boolean = internalMap.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<String, DiskMapFactory>>
        get() = internalMap.entries.map { entry -> object : MutableMap.MutableEntry<String, DiskMapFactory> {
                override val key: String
                    get() = entry.key
                override val value: DiskMapFactory
                    get() = entry.value.diskMapFactory
                override fun setValue(newValue: DiskMapFactory): DiskMapFactory = newValue
            }
        }.toMutableSet()

    override val keys: MutableSet<String>
        get() = internalMap.keys

    override val values: MutableCollection<DiskMapFactory>
        get() = internalMap.values.map { it.diskMapFactory }.toMutableList()

    override fun clear() = internalMap.clear()

    override fun putAll(from: Map<out String, DiskMapFactory>) {
        from.forEach { put(it.key, it.value) }
    }

    override fun remove(key: String): DiskMapFactory? = internalMap.remove(key)?.diskMapFactory
}

class DiskMapEntry(var accessed: Long, val diskMapFactory: DiskMapFactory)
