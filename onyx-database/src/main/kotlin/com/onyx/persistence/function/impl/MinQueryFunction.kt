package com.onyx.persistence.function.impl

import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * Get minimum value
 */
class MinQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.MIN), QueryFunction {

    override fun newInstance(): QueryFunction = MinQueryFunction(attribute)

    private var itemType:Class<*>? = null
    private var min: Any? = null
    private val valueLock = DefaultClosureLock()
    override fun getFunctionValue():Any? = min

    override fun preProcess(query: Query, value: Any?):Boolean {
        if(value != null && itemType == null)
            itemType = value.javaClass

        val valueDouble:Double = (value as? Number)?.toDouble() ?: 0.0
        val minDouble:Double = (min as? Number)?.toDouble() ?: 0.0

        return valueLock.perform {
            if (valueDouble < minDouble || min == null) {
                min = value
                return@perform true
            }
            return@perform false
        }
    }

}