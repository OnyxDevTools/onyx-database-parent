package com.onyx.interactors.index

import com.onyx.descriptor.IndexDescriptor
import com.onyx.exception.OnyxException

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Contract on how an index interacts
 */
interface IndexInteractor {

    /**
     * Validate an index mutation before the containing entity record is overwritten.
     *
     * Implementations must not mutate persistent state here. This hook exists for indexes whose
     * persisted topology has invariants (for example, one vector dimension per calibration) that
     * cannot be checked safely after the authoritative entity bytes have already changed.
     */
    @Throws(OnyxException::class)
    fun validateSave(oldIndexValue: Any?, indexValue: Any?, existingReferenceId: Long) = Unit

    /** Arms any durable mutation guard after every index has passed [validateSave]. */
    @Throws(OnyxException::class)
    fun prepareSave(oldIndexValue: Any?, indexValue: Any?, existingReferenceId: Long) = Unit

    /**
     * Save an index key with the record reference
     *
     * @param indexValue Index value to save
     * @param oldReferenceId Old entity reference for the index
     * @param newReferenceId New entity reference for the index
     */
    @Throws(OnyxException::class)
    fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long)

    /**
     * Save an index key when the caller has retained the value that was persisted before the record update.
     * Implementations that do not need the old value may continue to implement the legacy overload.
     */
    @Throws(OnyxException::class)
    fun save(oldIndexValue: Any?, indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) =
        save(indexValue, oldReferenceId, newReferenceId)

    /**
     * Delete an index key with a record reference
     *
     * @param reference Entity reference
     */
    @Throws(OnyxException::class)
    fun delete(reference: Long)

    /** Delete an index key without requiring a persistent reverse lookup by record reference. */
    @Throws(OnyxException::class)
    fun delete(indexValue: Any?, reference: Long) = delete(reference)

    /**
     * Find all index references
     *
     * @param indexValue Index value to find values for
     * @return References matching that index value
     */
    @Throws(OnyxException::class)
    fun findAll(indexValue: Any?): Map<Long, *>

    /**
     * Find all the references above and perhaps equal to the key parameter
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare above and equal or not.
     * @return A set of record references
     *
     * @throws OnyxException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    fun findAllAbove(indexValue: Any?, includeValue: Boolean): Set<Long>

    /**
     * Find all references between from and to values.  The includeFrom indicates greater than equal
     * and the includeToValue indicates less than equal
     *
     * @since 2.1.3 Optimize range queries
     */
    fun findAllBetween(fromValue:Any?, includeFromValue:Boolean, toValue:Any?, includeToValue:Boolean):Set<Long>

    /**
     * Find all the references blow and perhaps equal to the key parameter
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare below and equal or not.
     * @return A set of record references
     *
     * @throws OnyxException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    fun findAllBelow(indexValue: Any?, includeValue: Boolean): Set<Long>

    /**
     * Get Index descriptor
     *
     * @return Index descriptor for entity
     */
    val indexDescriptor: IndexDescriptor

    /**
     * Find all index references
     *
     * @return All index references
     */
    @Throws(OnyxException::class)
    fun findAllValues(): Set<Any>

    /**
     * Visit an explicitly approximate candidate prefix for one or more exact index values.
     *
     * [maxCandidates] is a shared physical posting-visit budget across [indexValues], not a
     * per-value limit. Implementations that cannot stop their physical posting traversal should
     * leave this unsupported rather than silently materializing an exhaustive result.
     *
     * @return number of posting IDs passed to [visitor]
     */
    fun visitApproximateCandidates(
        indexValues: List<Any>,
        maxCandidates: Int,
        visitor: (Long) -> Boolean
    ): Int = throw UnsupportedOperationException(
        "${this::class.java.name} does not support bounded approximate index candidates"
    )

    /**
     * Stream every posting for one or more exact index values until [visitor] returns `false`.
     *
     * Unlike [visitApproximateCandidates], this operation has no candidate-prefix semantics: a
     * `true` return from every callback means the supplied posting routes were exhausted. This is
     * used by bounded mutations that may have to inspect more postings than they ultimately mutate
     * before they can truthfully report that fewer than the requested number of rows remain.
     * Implementations must stop physical traversal when [visitor] returns `false` and must not
     * materialize a complete posting before invoking it.
     *
     * @return number of posting IDs passed to [visitor]
     */
    fun visitExactPostings(
        indexValues: List<Any>,
        visitor: (Long) -> Boolean
    ): Int = throw UnsupportedOperationException(
        "${this::class.java.name} does not support streaming exact index postings"
    )

    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     */
    @Throws(OnyxException::class)
    fun rebuild()

    /**
     * Clear all index references
     *
     * @since 9/26/2024
     */
    fun clear()

    /**
     * Shutdown the index interactor and release any resources
     *
     * @since 1.0.0
     */
    fun shutdown()

    /**
     * Delete all resources associated with this index.
     * This should delete any external files or resources that the index manages
     * outside of the main data file.
     *
     * @since 3.9.10
     */
    fun deleteResources()

    /**
     * Match indexed records against a lexical or semantic vector query.
     *
     * @param indexValue search text
     * @param limit Maximum number of items returned
     * @param maxCandidates Maximum number of candidates to consider for weight sorting
     *
     * @return Number of items matching search
     */
    fun matchAll(indexValue: Any?, limit: Int = 50, maxCandidates: Int = DEFAULT_MAX_CANDIDATES): Map<Long, Any?> = findAll(indexValue)
}

private const val DEFAULT_MAX_CANDIDATES: Int = 1_000
