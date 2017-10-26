package com.onyx.diskmap

/**
 * Created by Tim Osborn on 2/16/17.
 *
 * This is a contract of a sorted disk map.
 *
 * @since 1.2.0
 */
interface SortedDiskMap<K, out V> : Map<K, V> {

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @since 1.2.0
     * @return A Set of references
     */
    fun above(index: K, includeFirst: Boolean): Set<Long>

    /**
     * Find all references below and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    fun below(index: K, includeFirst: Boolean): Set<Long>
}
