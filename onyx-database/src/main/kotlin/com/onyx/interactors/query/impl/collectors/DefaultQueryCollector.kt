package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.lang.SortedList
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import java.util.*

/**
 * Default Query Collector used to get entities
 */
class DefaultQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor
) : BaseQueryCollector<IManagedEntity>(query, context, descriptor) {

    override var results: MutableCollection<IManagedEntity> = if(query.queryOrders?.isNotEmpty() == true) SortedList(EntityComparator(comparator)) else ArrayList()

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)
        if (entity == null)
            return

        synchronized(results) { results.add(entity) }
        increment()

        limit()
    }

}