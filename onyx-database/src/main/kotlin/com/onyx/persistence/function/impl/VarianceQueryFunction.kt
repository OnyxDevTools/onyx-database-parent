package com.onyx.persistence.function.impl

import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * Aggregates the variance within query results.
 * (This example uses the population variance formula.)
 */
class VarianceQueryFunction(attribute: String = "") : BaseQueryFunction(attribute, QueryFunctionType.VARIANCE), QueryFunction {

    override fun newInstance(): QueryFunction = VarianceQueryFunction(attribute)

    private var numberOfRecords: Int = 0
    private var itemType: Class<*>? = null

    private var variance: Any? = null

    // We'll track both the sum of values and the sum of their squares
    private var sumDouble: Double = 0.0
    private var sumSquareDouble: Double = 0.0

    private val valueLock = DefaultClosureLock()

    override fun getFunctionValue(): Any? = variance

    override fun preProcess(query: Query, value: Any?): Boolean {
        if (value != null && itemType == null) {
            itemType = value.javaClass
        }

        val valueDouble = (value as? Number)?.toDouble() ?: 0.0

        valueLock.perform {
            sumDouble += valueDouble
            sumSquareDouble += (valueDouble * valueDouble)
            numberOfRecords++
        }

        // Return false since we only aggregate
        return false
    }

    override fun postProcess(query: Query) {
        if (itemType != null && numberOfRecords > 0) {
            val n = numberOfRecords.toDouble()
            // Population variance:
            // variance = (sum(x^2)/n) - (sum(x)/n)^2
            val meanOfSquares = sumSquareDouble / n
            val squareOfMean = (sumDouble / n) * (sumDouble / n)
            val result = meanOfSquares - squareOfMean
            variance = result
        }
    }
}
