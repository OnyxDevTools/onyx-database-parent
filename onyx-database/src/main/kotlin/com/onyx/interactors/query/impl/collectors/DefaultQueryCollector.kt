package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query

/**
 * Default Query Collector used to get entities
 */
class DefaultQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor
) : BaseQueryCollector<IManagedEntity>(query, context, descriptor) {

    override var results: MutableCollection<IManagedEntity> = createResults { comparator.sortKeys(it) }

    private val referenceSortKeys = if (query.isLazy && query.shouldSortResults()) {
        HashMap<Reference, Array<Any?>>()
    } else null

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)
        if (entity == null)
            return

        // Computed ordering attributes may perform expensive scoring. Capture them on
        // this worker; heap comparisons and final sorting only inspect these values.
        val keys = if (query.shouldSortResults()) comparator.sortKeys(entity) else null
        if (!query.isLazy) {
            resultLock.perform {
                val ordered = results as? OrderedQueryResults<IManagedEntity>
                if (ordered != null) ordered.add(entity, keys!!) else results.add(entity)
            }
        } else if (referenceSortKeys != null) {
            referenceLock.perform { referenceSortKeys[reference] = keys!! }
        }
        increment()
        limit()
    }

    override fun finalizeResults() {
        if (isFinalized) return
        // Lazy queries and the query cache need the complete reference domain. Appending
        // during collection avoids sorted-array insertion for every matching reference.
        if (referenceSortKeys != null) {
            references.sortWith { first, second ->
                comparator.compareKeys(referenceSortKeys.getValue(first), referenceSortKeys.getValue(second))
            }
            referenceSortKeys.clear()
        }
        super.finalizeResults()
    }

}
