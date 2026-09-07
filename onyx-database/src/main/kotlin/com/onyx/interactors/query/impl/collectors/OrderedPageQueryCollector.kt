package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.extension.hydrateRelationships
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query

/** An already ordered and bounded page with an independently established full result count. */
internal class OrderedPageQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor,
) : BaseQueryCollector<IManagedEntity>(query, context, descriptor) {

    init {
        // Only page references are retained, so this collector cannot populate the query cache.
        shouldCacheResults = false
    }

    fun setTotalCount(count: Long) {
        if (count > 0 && count > context.maxCardinality) {
            throw MaxCardinalityExceededException(context.maxCardinality)
        }
        numberOfResults.set(count.toInt())
    }

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        references.add(reference)
        if (entity != null) results.add(entity)
    }

    override fun getLimitedReferences(): MutableList<Reference> = references

    override fun finalizeResults() {
        if (isFinalized) return
        // Pagination has already been applied by the tree. Hydrate only the returned page.
        if (!query.isLazy) results.forEach { it.hydrateRelationships(context) }
        isFinalized = true
    }
}
