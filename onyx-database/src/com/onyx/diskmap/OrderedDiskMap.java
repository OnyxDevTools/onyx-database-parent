package com.onyx.diskmap;

import java.util.Map;
import java.util.Set;

/**
 * Created by tosborn1 on 2/16/17.
 *
 * This is a contract of a sorted disk map.
 *
 * @since 1.2.0
 */
public interface OrderedDiskMap<K,V> extends Map<K,V> {

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @since 1.2.0
     * @return A Set of references
     */
    Set<Long> above(K index, boolean includeFirst);

    /**
     * Find all references below and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    Set<Long> below(K index, boolean includeFirst);
}
