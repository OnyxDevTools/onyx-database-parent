package com.onyx.persistence.function.impl

import com.onyx.extension.common.castTo
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * Aggregates the median value within query results.
 */
class MedianQueryFunction(attribute: String = "") : BaseQueryFunction(attribute, QueryFunctionType.MEDIAN), QueryFunction {

    override fun newInstance(): QueryFunction = MedianQueryFunction(attribute)

    // Store all values so we can compute median after sorting.
    private val allValues = mutableListOf<Double>()
    private var itemType: Class<*>? = null
    private var median: Any? = null

    private val valueLock = DefaultClosureLock()

    override fun getFunctionValue(): Any? = median

    override fun preProcess(query: Query, value: Any?): Boolean {
        if (value != null) {
            // Save the type if not yet set.
            if (itemType == null) {
                itemType = value.javaClass
            }

            // Convert the value to double for consistent storage.
            val numericValue = (value as? Number)?.toDouble() ?: 0.0

            // Protect the list with a lock for thread-safe access.
            valueLock.perform {
                allValues.add(numericValue)
            }
        }

        // Return false since we're just aggregating here.
        return false
    }

    override fun postProcess(query: Query) {
        if (allValues.isNotEmpty() && itemType != null) {
            // Sort the list of numeric values.
            allValues.sort()

            val size = allValues.size
            val middle = size / 2

            val result = if (size % 2 == 1) {
                // Odd number of elements: pick the middle.
                allValues[middle]
            } else {
                // Even number of elements: average the middle two.
                (allValues[middle - 1] + allValues[middle]) / 2.0
            }

            // Cast it back to the original type if needed
            median = result.castTo(itemType!!)
        }
    }
}
