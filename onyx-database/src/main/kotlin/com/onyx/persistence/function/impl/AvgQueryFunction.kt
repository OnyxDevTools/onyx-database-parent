package com.onyx.persistence.function.impl

import com.onyx.extension.common.castTo
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * Aggregates the average value within query results
 */
class AvgQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.AVG), QueryFunction {

    override fun newInstance(): QueryFunction = AvgQueryFunction(attribute)

    private var numberOfRecords:Int = 0
    private var itemType:Class<*>? = null
    private var avg: Any? = null
    private var sumDouble: Double = 0.0

    override fun getFunctionValue():Any? = avg

    override fun preProcess(query: Query, value: Any?):Boolean {
        if(value != null && itemType == null)
            itemType = value.javaClass

        val valueDouble:Double = (value as? Number)?.toDouble() ?: 0.0

        synchronized(this) {
            sumDouble += valueDouble
            numberOfRecords++
        }
        return false
    }

    override fun postProcess(query: Query) {
        if(itemType != null) {
            val recordDouble:Double = numberOfRecords.toDouble()
            avg = (sumDouble / recordDouble).castTo(itemType!!)
        }
    }
}