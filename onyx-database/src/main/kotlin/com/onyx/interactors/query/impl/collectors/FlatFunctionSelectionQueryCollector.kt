package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import kotlin.collections.HashMap

/**
 * Get a single result when executing selection functions that require aggregation without grouping
 */
class FlatFunctionSelectionQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor
) : BaseQueryCollector<Any?>(query, context, descriptor) {

    private var result = OptimisticLockingMap(HashMap<String, Any?>())
    private val otherSelections = selections.filter { it.function?.type?.isGroupFunction != true }
    private val selectionFunctions = selections.filter { it.function?.type?.isGroupFunction == true }

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        super.collect(reference, entity)

        if(entity == null)
            return

        selectionFunctions.forEach {
            val selectionValue = comparator.getAttribute(it, entity, context)
            if(it.function?.preProcess(query, selectionValue) == true) {
                otherSelections.forEach { selection ->
                    if (selection.function == null)
                        result[selection.selection] = comparator.getAttribute(selection, entity, context)
                    else
                        result[selection.selection] = selection.function.execute(comparator.getAttribute(selection, entity, context))
                }
            }
        }
    }

    override fun finalizeResults() {
        if(!isFinalized) {
            selections.forEach {
                selectionFunctions.forEach {
                    it.function?.postProcess(query)
                    result[it.selection] = it.function?.getFunctionValue()
                }
            }
            results = arrayListOf(HashMap(result))
            numberOfResults.set(1)
            isFinalized = true
        }
    }

}