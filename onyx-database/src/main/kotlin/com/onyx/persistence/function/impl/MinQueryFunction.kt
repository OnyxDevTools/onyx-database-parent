package com.onyx.persistence.function.impl

import com.onyx.extension.common.forceCompare
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryFunctionType
import java.util.*

/**
 * Get minimum value
 */
class MinQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.MIN), QueryFunction {

    override fun newInstance(): QueryFunction = MinQueryFunction(attribute)

    private var itemType:Class<*>? = null
    private var min: Any? = UNDEFINED
    private val valueLock = DefaultClosureLock()
    override fun getFunctionValue():Any? = min

    override fun preProcess(query: Query, value: Any?):Boolean {
        if(value != null && itemType == null)
            itemType = value.javaClass

        return valueLock.perform {
            if (min === UNDEFINED)
                min = value
            if(min.forceCompare(value, QueryCriteriaOperator.LESS_THAN_EQUAL)) {
                min = value
                return@perform true
            }
            return@perform false
        }
    }

    companion object {
        val UNDEFINED = Any()
    }
}