package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.extension.common.parallelForEach
import com.onyx.extension.hydrateRelationships
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.cache.impl.DefaultQueryCacheInteractor
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.data.QueryAttributeResource
import com.onyx.interactors.query.data.QuerySortComparator
import com.onyx.interactors.record.data.Reference
import com.onyx.lang.SortedHashSet
import com.onyx.lang.SortedList
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class bores the responsibility of collecting results from a query.  It contains the base shared functionality
 * for all extending collectors
 */
abstract class BaseQueryCollector<T>(
    var query: Query,
    var context: SchemaContext,
    var descriptor: EntityDescriptor
) : QueryCollector<T> {

    // References used for caching
    override val references:MutableList<Reference> = ArrayList()

    // Used for real-time limiting of results so we know our offset index of truncated items
    private var startIndex:Int = 0

    protected var shouldCacheResults:Boolean = true
    protected var referenceLock = DefaultClosureLock()
    protected var resultLock = DefaultClosureLock()

    // Used to compare values and a base comparator that pulls info from the store rather than memory
    protected val comparator: QuerySortComparator by lazy { QuerySortComparator(query, query.queryOrders?.toTypedArray() ?: emptyArray(), descriptor, context) }

    @Suppress("UNCHECKED_CAST")
    override var results: MutableCollection<T> =
            if(query.isDistinct) {
                if(query.queryOrders?.isNotEmpty() == true) {
                    SortedHashSet(EntityComparator(comparator)) as MutableCollection<T>
                } else {
                    HashSet()
                }
            } else
                if(query.queryOrders?.isNotEmpty() == true) SortedList(EntityComparator(comparator)) as MutableCollection<T> else ArrayList()

    // Selection Query Attributes
    protected val selections:List<QueryAttributeResource> by lazy { if(query.selections == null) emptyList() else QueryAttributeResource.create(query.selections!!.map { it }.toTypedArray(), descriptor, query, context) }

    // Order by query attributes
    protected val orders:List<QueryAttributeResource> by lazy { if(query.queryOrders == null) emptyList() else QueryAttributeResource.create(query.queryOrders!!.map { it.attribute }.toTypedArray(), descriptor, query, context) }

    // Marker for finalizing.  If it is finalized, it will not need it again
    protected var isFinalized:Boolean = false

    // Number of total results.  This is kept separate from the references in the event a query result becomes too large
    // to store references.
    protected val numberOfResults:AtomicInteger = AtomicInteger(0)

    /**
     * Contrived list of attributes so the query collector knows what to grab from the store
     */
    protected val allQueryAttributes:List<QueryAttributeResource> by lazy {
        val groupStrings = query.groupBy ?: emptyList()
        val orderByStrings = query.queryOrders?.map { it.attribute }?.toList() ?: ArrayList()
        val selectionStrings = query.selections ?: emptyList()
        val allStrings = groupStrings + orderByStrings + selectionStrings
        QueryAttributeResource.create(allStrings.toHashSet().toTypedArray(), descriptor, query, context)
    }

    /**
     * Set reference set.  Used from setting references from a cache
     *
     * @since 2.1.3
     */
    override fun setReferenceSet(value: MutableSet<Reference>) {
        if(value.size > 1000)
            value.parallelForEach { collect(it, it.toManagedEntity(context, descriptor)) }
        else
            value.forEach { collect(it, it.toManagedEntity(context, descriptor)) }
    }

    /**
     * Collect and aggregate a query result.  This just keeps track of cached references
     *
     * @since 2.1.3
     */
    override fun collect(reference: Reference, entity: IManagedEntity?) {
        if(entity == null)
            return

        if(shouldCacheResults) {
            referenceLock.perform {
                if (references.size >= DefaultQueryCacheInteractor.MAX_CACHED_REFERENCES) {
                    shouldCacheResults = false
                    references.clear()
                    (references as? ArrayList)?.trimToSize()
                } else {
                    references.add(reference)
                }
            }
        }
    }

    protected fun increment() {
        numberOfResults.incrementAndGet()
    }

    /**
     * Should cache the results.  This tells the query caching that the result size is acceptable or too large to cache.
     *
     * @since 2.1.3
     */
    override fun shouldCacheResults():Boolean = shouldCacheResults

    /**
     * Limit the results.  This is done per item so that there are less records to sort in the event the query is ordered
     *
     * @since 2.1.3
     */
    protected open fun limit():Boolean {
        if(query.firstRow > 0
                && startIndex <= query.firstRow
                && query.maxResults > 0
                && query.maxResults < results.size) {

            return resultLock.perform {
                if(query.maxResults < results.size) {
                    results.remove(results.first())
                    startIndex++
                    return@perform true
                }
                return@perform false
            }
        } else if (query.maxResults > 0) {
            return resultLock.perform {
                if(query.maxResults < results.size) {
                    results.remove(results.last())
                    return@perform true
                }
                return@perform false
            }
        }
        return false
    }

    /**
     * Limit the number of references in the event we only care about references.  This is done for update and delete
     * queries so we do not care about caching or returning result items.
     *
     * @since 2.1.3
     */
    protected open fun limitReferences():Boolean {
        if(query.firstRow > 0
                && startIndex <= query.firstRow
                && query.maxResults > 0) {
            return referenceLock.perform {
                if (query.maxResults < references.size) {
                        references.removeAt(0)
                        startIndex++
                        return@perform true
                    }
                    return@perform false
                }
        } else if (query.maxResults > 0) {
            return referenceLock.perform {
                if (query.maxResults < references.size) {
                    references.removeAt(references.lastIndex)
                    return@perform true
                }
                return@perform false
            }
        }
        return false
    }

    /**
     * Finalize and format the results
     *
     * @since 2.1.3
     */
    override fun finalizeResults() {
        if(!isFinalized) {

            // If it is only entity results, hydrate the relationships
            if (query.groupBy?.isEmpty() != false
                    && query.selections?.isEmpty() != false) {
                if(!query.isLazy)
                    results.forEach { (it as IManagedEntity?)?.hydrateRelationships(context) }
            }
            // Selection results.  Here we remove unselected fields that may have been part of the query orders or groups
            else if(query.selections?.size ?: 0 > 0
                && query.selections!!.size != allQueryAttributes.size) {

                val itemsToRemove = allQueryAttributes.map { it.selection } - query.selections!!
                if(itemsToRemove.isNotEmpty()) {
                    results.forEach { record ->
                        itemsToRemove.forEach {
                            @Suppress("UNCHECKED_CAST")
                            (record as MutableMap<String, Any?>).remove(it)
                        }
                    }
                }
            }

            // Limit results.  This is dome to ensure the results are limited.  If the query was marked as distinct
            // real-time limiting may not have been ran
            if(query.firstRow > 0 || query.maxResults > 0) {
                @Suppress("ControlFlowWithEmptyBody")
                while (limit()) {}
            }

            @Suppress("UNCHECKED_CAST")
            if(results is Set<*>)
                results = results.toList() as MutableCollection<T>

            isFinalized = true
        }
    }

    /**
     * Get final limited references
     *
     * @since 2.1.3
     */
    override fun getLimitedReferences(): MutableList<Reference> =
        if(query.firstRow > 0 || query.maxResults > 0) {
            if(query.firstRow >= references.size)
                ArrayList()
            else {
                val end = if(query.maxResults <= 0) references.size else if((query.firstRow + query.maxResults) >= references.size) (references.size) else (query.firstRow + query.maxResults)
                references.subList(query.firstRow, end)
            }
        }
        else
            references


    /**
     * Get all selection data items and put them into a hash map.  This includes query orders, groups and selections.
     * It contains all data elements that can be used to aggregate a select query
     */
    protected fun getSelectionRecord(entity: IManagedEntity) : HashMap<String, Any?> {
        val selectionResult = HashMap<String, Any?>()
        allQueryAttributes.forEach { selection ->
            if(selection.function == null)
                selectionResult[selection.selection] = comparator.getAttribute(selection, entity, context)
            else
                selectionResult[selection.selection] = selection.function.execute(comparator.getAttribute(selection, entity, context))
        }

        return selectionResult
    }

    /**
     * Get the final number of results aggregated
     */
    override fun getNumberOfResults(): Int = numberOfResults.get()

    /**
     * Class used to compare entity order
     */
    internal class EntityComparator(private val innerComparator: QuerySortComparator) : Comparator<IManagedEntity> {
        override fun compare(o1: IManagedEntity, o2: IManagedEntity): Int = innerComparator.compare(o1, o2)
    }

    /**
     * Class used to compare reference order
     */
    internal class ReferenceComparator(private val innerComparator: QuerySortComparator) : Comparator<Reference> {
        override fun compare(o1: Reference, o2: Reference): Int = innerComparator.compare(o1, o2)
    }

    /**
     * Class used to compare map order
     */
    internal class MapComparator(private val innerComparator:QuerySortComparator) : Comparator<Map<String, Any?>> {
        override fun compare(o1: Map<String, Any?>, o2: Map<String, Any?>): Int = innerComparator.compare(o1, o2)
    }

}