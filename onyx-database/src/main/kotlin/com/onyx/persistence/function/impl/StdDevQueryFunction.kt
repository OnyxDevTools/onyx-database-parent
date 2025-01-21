package com.onyx.persistence.function.impl

import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType
import kotlin.math.sqrt

/**
 * Aggregates the standard deviation within query results.
 */
class StdDevQueryFunction(attribute: String = "") : BaseQueryFunction(attribute, QueryFunctionType.STD), QueryFunction {

    override fun newInstance(): QueryFunction = StdDevQueryFunction(attribute)

    private var numberOfRecords: Int = 0
    private var itemType: Class<*>? = null
    private var stdDev: Any? = null

    // We will need both sum of values and sum of squares.
    private var sumDouble: Double = 0.0
    private var sumSquareDouble: Double = 0.0

    private val valueLock = DefaultClosureLock()

    override fun getFunctionValue(): Any? = stdDev

    override fun preProcess(query: Query, value: Any?): Boolean {
        if (value != null && itemType == null) {
            itemType = value.javaClass
        }

        val valueDouble: Double = (value as? Number)?.toDouble() ?: 0.0

        valueLock.perform {
            sumDouble += valueDouble
            sumSquareDouble += (valueDouble * valueDouble)
            numberOfRecords++
        }

        // Return false since we are only aggregating (like the AvgQueryFunction).
        return false
    }

    override fun postProcess(query: Query) {
        if (itemType != null && numberOfRecords > 0) {
            val n = numberOfRecords.toDouble()

            // Population standard deviation formula:
            // σ = sqrt( (sum(x^2) / n) - ( (sum(x) / n) ^ 2 ) )
            val meanOfSquares = sumSquareDouble / n
            val squareOfMean = (sumDouble / n) * (sumDouble / n)
            val result = sqrt(meanOfSquares - squareOfMean)

            // Cast back to the original item type
            stdDev = result
        }
    }
}
