package com.onyx.persistence.function.impl

import com.onyx.extension.common.forceCompare
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.function.impl.MinQueryFunction.Companion.UNDEFINED
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryFunctionType

/**
 * Get Max value
 */
class MaxQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.MAX), QueryFunction {

    override fun newInstance(): QueryFunction = MaxQueryFunction(attribute)

    private var itemType:Class<*>? = null
    private var max: Any? = UNDEFINED
    private val valueLock = DefaultClosureLock()

    override fun getFunctionValue():Any? = max

    override fun preProcess(query: Query, value: Any?):Boolean {
        if(value != null && itemType == null)
            itemType = value.javaClass

        return valueLock.perform {
            if (max === UNDEFINED)
                max = value
            if(max.forceCompare(value, QueryCriteriaOperator.GREATER_THAN_EQUAL)) {
                max = value
                return@perform true
            }
            return@perform false
        }
    }
}