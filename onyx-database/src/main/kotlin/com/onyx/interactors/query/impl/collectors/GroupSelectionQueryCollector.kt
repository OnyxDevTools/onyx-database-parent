package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.query.data.QueryAttributeResource
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query

/**
 * Group results without functions
 */
class GroupSelectionQueryCollector(
        query: Query,
        context: SchemaContext,
        descriptor: EntityDescriptor
) : BasicSelectionQueryCollector(query, context, descriptor) {

    private val groupedResults = HashSet<Map<String, Any?>>()
    private val groupAttributes:List<QueryAttributeResource> by lazy {
        val groupStrings = query.groupBy ?: emptyList()
        QueryAttributeResource.create(groupStrings.toHashSet().toTypedArray(), descriptor, query, context)
    }

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        if(entity == null)
            return

        val selections = getGroupResults(entity)
        val selectionResult = getSelectionRecord(entity)

        resultLock.perform {
            if (!groupedResults.contains(selections)) {
                if(results.add(selectionResult))
                    increment()
                groupedResults.add(selections)
            }
        }

        limit()

    }

    /**
     * Get groupings
     */
    private fun getGroupResults(entity: IManagedEntity) : HashMap<String, Any?> {
        val selectionResult = HashMap<String, Any?>()
        groupAttributes.forEach { selection ->
            if(selection.function == null)
                selectionResult[selection.selection] = comparator.getAttribute(selection, entity, context)
            else
                selectionResult[selection.selection] = selection.function.execute(comparator.getAttribute(selection, entity, context))
        }

        return selectionResult
    }

}
