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

    override var results: MutableCollection<IManagedEntity> = createResults { EntityComparator(comparator) }

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)
        if (entity == null)
            return

        if(!query.isLazy)
            resultLock.perform { results.add(entity) }
        increment()
        limit()
    }

    override fun finalizeResults() {
        if (isFinalized) return
        // Lazy queries and the query cache need the complete reference domain. Appending
        // during collection avoids sorted-array insertion for every matching reference.
        if (query.isLazy && query.shouldSortResults()) references.sortWith(comparator)
        super.finalizeResults()
    }

}
