package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query

class UpdateQueryCollector(
        query: Query,
        context: SchemaContext,
        descriptor: EntityDescriptor
) : BaseQueryCollector<Reference>(query, context, descriptor) {

    init {
        shouldCacheResults = false
    }

    private val orderedReferences = if (query.shouldSortResults()) {
        val retainedRows = if (query.maxResults > 0) {
            query.firstRow.coerceAtLeast(0).toLong() + query.maxResults.toLong()
        } else null
        OrderedQueryResults<Reference>(comparator::compareKeys, retainedRows)
    } else null

    override fun collect(reference: Reference, entity: IManagedEntity?) {

        if(entity == null)
            return

        val keys = orderedReferences?.let { comparator.sortKeys(entity) }
        referenceLock.perform {
            if (orderedReferences != null) {
                orderedReferences.add(reference, keys!!)
            } else {
                references.add(reference)
                limitReferences()
            }
        }
        increment()
    }

    override fun finalizeResults() {
        if (isFinalized) return
        orderedReferences?.let { ordered ->
            references.addAll(ordered.sortedResults().drop(query.firstRow.coerceAtLeast(0)))
            ordered.clear()
        }
        isFinalized = true
    }

    override fun getLimitedReferences(): MutableList<Reference> =
        if (orderedReferences != null) references else super.getLimitedReferences()

}
