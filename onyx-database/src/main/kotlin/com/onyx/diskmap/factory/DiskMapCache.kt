package com.onyx.diskmap.factory

import com.onyx.persistence.context.impl.DefaultSchemaContext
import java.util.concurrent.TimeUnit

/**
 * @author Tim Osborn
 *
 * This class helps reduce the amount of open files a database can have. If a file is not accessed within a minute,
 * it is eligible to be evicted from the cache.  In the event the use chooses memory mapped storage, this will prevent
 * a large growing memory footprint and therefore improve performance
 */
open class DiskMapCache : HashMap<String, DiskMapFactory>() {
    private val internalMap = object : LinkedHashMap<String, DefaultSchemaContext.DiskMapEntry>(200, .75f, true) {
        override fun removeEldestEntry(entry: MutableMap.MutableEntry<String, DefaultSchemaContext.DiskMapEntry>?): Boolean {
            val remove = ((entry!!.value.accessed + TimeUnit.MINUTES.toMillis(1)) < System.currentTimeMillis())
            if(remove) {
                entry.value.diskMapFactory.close()
            }
            return remove
        }

        override fun get(key: String): DefaultSchemaContext.DiskMapEntry? {
            val value = super.get(key)
            value?.let {
                it.accessed = System.currentTimeMillis()
            }
            return value
        }
    }

    override fun get(key: String): DiskMapFactory? = internalMap[key]?.diskMapFactory

    override fun put(key: String, value: DiskMapFactory): DiskMapFactory {
        internalMap[key] = DefaultSchemaContext.DiskMapEntry(System.currentTimeMillis(), value)
        return value
    }
}
