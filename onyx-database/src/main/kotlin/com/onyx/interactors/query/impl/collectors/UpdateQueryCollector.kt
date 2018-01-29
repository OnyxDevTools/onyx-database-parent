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

    override val references: MutableList<Reference> = if(query.shouldSortResults()) SortedList(ReferenceComparator(comparator)) else ArrayList()

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)
        if(entity == null)
            return

        increment()
        limitReferences()
    }

    override fun finalizeResults() = Unit

}