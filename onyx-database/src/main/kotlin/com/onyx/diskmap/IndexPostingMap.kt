package com.onyx.diskmap

/**
 * Persistent ordered set of secondary-index postings.
 *
 * The logical key is `(indexValue, recordId)`, but the two components remain
 * separate at the API boundary so callers never need to allocate a tuple
 * wrapper. Implementations store the record ID directly in BTree leaf slots.
 */
interface IndexPostingMap {

    val valueType: Class<*>

    /** Add a posting, returning true only when it was not already present. */
    fun add(indexValue: Any, recordId: Long): Boolean

    /** Remove an exact posting, returning true when it existed. */
    fun remove(indexValue: Any, recordId: Long): Boolean

    /** Test whether an exact posting exists. */
    fun contains(indexValue: Any, recordId: Long): Boolean

    /**
     * Visit record IDs in tuple order between optional tuple bounds. A null
     * value component represents an unbounded side; its record-ID component is
     * ignored.
     */
    fun forEachRecordIdInRange(
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean,
        action: (Long) -> Unit
    )

    /**
     * Visit at most [maxVisits] record IDs in tuple order, stopping as soon as [visitor] returns
     * `false`. The return value is the number of IDs passed to [visitor].
     *
     * Native posting maps override this method so reaching either bound also stops B+ tree page
     * traversal. Custom maps must provide an equivalent physical stop; the default deliberately
     * fails instead of disguising exhaustive traversal as bounded work.
     */
    fun visitRecordIdsInRange(
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean,
        maxVisits: Int,
        visitor: (Long) -> Boolean
    ): Int = throw UnsupportedOperationException(
        "${this::class.java.name} does not support physically bounded posting traversal"
    )

    /** Visit each distinct indexed value once, in index order. */
    fun forEachDistinctValue(action: (Any) -> Unit)

    fun longSize(): Long

    fun clear()

    fun clearCache()
}
