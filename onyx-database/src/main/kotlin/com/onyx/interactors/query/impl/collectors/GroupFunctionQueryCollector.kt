package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.query.data.QueryAttributeResource
import com.onyx.interactors.record.data.Reference
import com.onyx.lang.SortedList
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query

/**
 * Group results and aggregate query functions
 */
class GroupFunctionQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor
) : BaseQueryCollector<Map<String, Any?>>(query, context, descriptor) {

    // Override to define sort comparator
    override var results: MutableCollection<Map<String, Any?>> = if(query.shouldSortResults()) SortedList(MapComparator(comparator)) else ArrayList()

    // Group results
    private val groups = HashMap<List<Any?>, MutableMap<String, Any?>>()

    // Functions that require group aggregation
    private val selectionFunctions = selections.filter { it.function?.type?.isGroupFunction == true }

    /**
     * Group attributes
     */
    private val groupAttributes:List<QueryAttributeResource> by lazy {
        val groupStrings = query.groupBy ?: emptyList()
        QueryAttributeResource.create(groupStrings.toHashSet().toTypedArray(), descriptor, query, context)
    }

    /**
     * Collect the results and aggregate them.  Do NOT call super because we
     * do not want to collect them in the result set
     *
     * @since 2.1.3
     */
    override fun collect(reference: Reference, entity: IManagedEntity?) {

        if(entity == null)
            return

        val groupResult = getGroupResults(entity)

        val map = synchronized(groups) {
            groups.getOrPut(groupResult) {
                increment()
                HashMap()
            }
        }

        synchronized(map) {
            allQueryAttributes.forEach { attribute ->
                if (attribute.function?.type?.isGroupFunction == true) {
                    (map.getOrPut(attribute.selection) {
                        attribute.function.newInstance()
                    } as QueryFunction).preProcess(query, comparator.getAttribute(attribute, entity, context)) // Process function
                } else {
                    map.getOrPut(attribute.selection) {
                        comparator.getAttribute(attribute, entity, context)
                    }
                }
            }
        }

    }

    /**
     * Get the selection criteria for groupings
     */
    private fun getGroupResults(entity: IManagedEntity) : List<Any?> {
        val selectionResult = ArrayList<Any?>()
        groupAttributes.forEach { selection ->
            if(selection.function == null)
                selectionResult.add(comparator.getAttribute(selection, entity, context))
            else
                selectionResult.add(selection.function.execute(comparator.getAttribute(selection, entity, context)))
        }

        return selectionResult
    }

    /**
     * Re-format the results
     */
    override fun finalizeResults() {
        if(!isFinalized) {
            groups.values.forEach { group ->
                selectionFunctions.forEach {
                    val function = group[it.selection] as QueryFunction
                    function.postProcess(query)
                    group[it.selection] = function.getFunctionValue()
                }

                results.add(group)
                limit()
            }

            // Remove un-selected attributes
            val itemsToRemove = allQueryAttributes.map { it.selection } - query.selections!!
            if(itemsToRemove.isNotEmpty()) {
                results.forEach { record ->
                    itemsToRemove.forEach {
                        @Suppress("UNCHECKED_CAST")
                        (record as MutableMap<String, Any?>).remove(it)
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            if(results is Set<*>)
                results = results.toList() as MutableCollection<Map<String, Any?>>
            isFinalized = true
        }
    }

}