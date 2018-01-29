package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.lang.SortedList
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import java.util.ArrayList
import java.util.TreeSet

/**
 * Used for basic selection queries
 */
open class BasicSelectionQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor
) : BaseQueryCollector<Map<String, Any?>>(query, context, descriptor) {

    override var results: MutableCollection<Map<String, Any?>> =
            if(query.isDistinct) {
                if(query.queryOrders?.isNotEmpty() == true) {
                    TreeSet(MapComparator(comparator))
                } else {
                    HashSet()
                }
            } else
                if(query.queryOrders?.isNotEmpty() == true) SortedList(MapComparator(comparator)) else ArrayList()

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)

        if(entity == null)
            return

        val selectionResult = getSelectionRecord(entity)

        synchronized(results) {
            if(results.add(selectionResult))
                increment()
        }
        limit()
    }

}