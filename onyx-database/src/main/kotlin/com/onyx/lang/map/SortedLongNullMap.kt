package com.onyx.lang.map

import java.util.AbstractMap.SimpleImmutableEntry

/**
 * Detached index-reference snapshot. Null values are implicit and keys occupy primitive array
 * slots; collecting a posting does not retain a hash node and boxed key for every match.
 * Map views box keys only when consumed, and membership uses binary search.
 */
internal class SortedLongNullMap private constructor(
    private val references: LongArray,
    override val size: Int
) : AbstractMap<Long, Any?>() {

    override fun get(key: Long): Any? = null

    override fun containsKey(key: Long): Boolean = references.binarySearch(key, 0, size) >= 0

    override fun containsValue(value: Any?): Boolean = value == null && size != 0

    override val keys: Set<Long>
        get() = object : AbstractSet<Long>() {
            override val size: Int get() = this@SortedLongNullMap.size

            override fun contains(element: Long): Boolean = containsKey(element)

            override fun iterator(): LongIterator = object : LongIterator() {
                private var position = 0

                override fun hasNext(): Boolean = position < size

                override fun nextLong(): Long {
                    if (!hasNext()) throw NoSuchElementException()
                    return references[position++]
                }
            }

            override fun hashCode(): Int = this@SortedLongNullMap.hashCode()
        }

    override val entries: Set<Map.Entry<Long, Any?>>
        get() = object : AbstractSet<Map.Entry<Long, Any?>>() {
            override val size: Int get() = this@SortedLongNullMap.size

            override fun contains(element: Map.Entry<Long, Any?>): Boolean {
                val key: Any? = element.key
                return element.value == null && key is Long && containsKey(key)
            }

            override fun iterator(): Iterator<Map.Entry<Long, Any?>> =
                object : Iterator<Map.Entry<Long, Any?>> {
                    private var position = 0

                    override fun hasNext(): Boolean = position < size

                    override fun next(): Map.Entry<Long, Any?> {
                        if (!hasNext()) throw NoSuchElementException()
                        return SimpleImmutableEntry(references[position++], null)
                    }
                }

            override fun hashCode(): Int = this@SortedLongNullMap.hashCode()
        }

    override val values: Collection<Any?>
        get() = object : AbstractList<Any?>() {
            override val size: Int get() = this@SortedLongNullMap.size

            override fun get(index: Int): Any? {
                if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index: $index, size: $size")
                return null
            }

            override fun contains(element: Any?): Boolean = containsValue(element)
        }

    override fun hashCode(): Int {
        var hash = 0
        for (index in 0 until size) hash += references[index].hashCode()
        return hash
    }

    /** Consumes unique keys in ascending posting order and transfers array ownership on [build]. */
    class Builder {
        private var references = LongArray(0)
        private var size = 0

        fun add(reference: Long) {
            if (size > 0) {
                val previous = references[size - 1]
                if (previous == reference) return
                require(previous < reference) { "Index references must be visited in ascending order" }
            }
            if (size == references.size) {
                val capacity = if (size == 0) 16 else (size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                check(capacity > size) { "Too many index references for a map snapshot" }
                references = references.copyOf(capacity)
            }
            references[size++] = reference
        }

        fun build(): Map<Long, Any?> {
            if (size == 0) return emptyMap()
            val result = SortedLongNullMap(references, size)
            // Keep growth capacity instead of copying the entire result again.
            references = LongArray(0)
            size = 0
            return result
        }
    }
}
