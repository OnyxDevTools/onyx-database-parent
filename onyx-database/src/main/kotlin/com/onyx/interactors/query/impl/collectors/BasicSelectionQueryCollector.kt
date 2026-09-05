package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query

/**
 * Used for basic selection queries
 */
open class BasicSelectionQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor
) : BaseQueryCollector<Map<String, Any?>>(query, context, descriptor) {

    override var results: MutableCollection<Map<String, Any?>> = createResults { MapComparator(comparator) }

    // Track uniqueness independently of the retained page and of equal ORDER BY keys.
    private val distinctResults = if (query.isDistinct) HashSet<Map<String, Any?>>() else null

    /** Called under resultLock, including for grouped selections. */
    protected fun addSelectionResult(selectionResult: Map<String, Any?>) {
        if (distinctResults?.add(selectionResult) == false) return
        results.add(selectionResult)
        // A heap may discard this row, but it still contributes to the full result count.
        increment()
    }

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)

        if(entity == null)
            return

        val selectionResult = getSelectionRecord(entity, reference)

        resultLock.perform {
            addSelectionResult(selectionResult)
        }
        limit()
    }

}
