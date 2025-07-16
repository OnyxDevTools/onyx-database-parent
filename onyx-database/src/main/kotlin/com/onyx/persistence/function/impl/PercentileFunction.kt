package com.onyx.persistence.function.impl

import com.onyx.extension.common.castTo
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType
import kotlin.math.floor

/**
 * Function to calculate the specified percentile of the attribute values.
 * The percentile must be a value between 0 and 100.
 */
class PercentileQueryFunction(attribute: String = "", val percentile: Double) : BaseQueryFunction(attribute, QueryFunctionType.PERCENTILE), QueryFunction {

    override fun newInstance(): QueryFunction = PercentileQueryFunction(attribute, this.percentile)

    private val allValues = mutableListOf<Double>()
    private var itemType: Class<*>? = null
    private var result: Any? = null
    private val valueLock = DefaultClosureLock()

    override fun getFunctionValue(): Any? = result

    /**
     * Collects attribute values and converts them to doubles for consistent processing.
     */
    override fun preProcess(query: Query, value: Any?): Boolean {
        if (value != null) {
            if (itemType == null) {
                itemType = value.javaClass
            }
            val numericValue = (value as? Number)?.toDouble() ?: 0.0
            valueLock.perform {
                allValues.add(numericValue)
            }
        }
        return false
    }

    /**
     * Sorts the collected values and computes the percentile using linear interpolation.
     */
    override fun postProcess(query: Query) {
        if (allValues.isNotEmpty() && itemType != null) {
            allValues.sort()
            val n = allValues.size
            val p = percentile / 100.0
            val index = p * (n - 1)
            val k = floor(index).toInt()
            val d = index - k
            val percentileValue = if (d == 0.0) {
                allValues[k]
            } else {
                (1 - d) * allValues[k] + d * allValues[k + 1]
            }
            result = percentileValue.castTo(itemType!!)
        }
    }
}