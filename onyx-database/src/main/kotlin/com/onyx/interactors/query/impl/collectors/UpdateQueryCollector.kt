package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.lang.SortedList
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

    override val references: MutableList<Reference> = if(query.shouldSortResults()) SortedList(ReferenceComparator(comparator)) else ArrayList()

    override fun collect(reference: Reference, entity: IManagedEntity?) {

        if(entity == null)
            return

        referenceLock.perform {
            references.add(reference)
        }
        increment()
        limitReferences()
    }

    override fun finalizeResults() = Unit

}