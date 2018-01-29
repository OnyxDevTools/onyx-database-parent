package com.onyx.persistence.function.impl

import com.onyx.extension.common.castTo
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * Query function that calculates the sum
 */
class SumQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.SUM), QueryFunction {

    private var sum:Any? = null
    private var itemType:Class<*>? = null
    private var sumDouble: Double = 0.0

    override fun getFunctionValue():Any? = sum

    override fun preProcess(query: Query, value: Any?): Boolean = synchronized(this) {
        if(value != null && itemType == null)
            itemType = value.javaClass

        val valueDouble:Double = (value as? Number)?.toDouble() ?: 0.0
        sumDouble += valueDouble
        return false
    }

    override fun postProcess(query: Query) {
        if(itemType != null)
            sum = sumDouble.castTo(itemType!!)
    }

    override fun newInstance(): QueryFunction = SumQueryFunction(attribute)

}