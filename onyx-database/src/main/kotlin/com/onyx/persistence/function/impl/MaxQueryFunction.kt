package com.onyx.persistence.function.impl

import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * Get Max value
 */
class MaxQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.MAX), QueryFunction {

    override fun newInstance(): QueryFunction = MaxQueryFunction(attribute)

    private var itemType:Class<*>? = null
    private var max: Any? = null
    private val valueLock = DefaultClosureLock()

    override fun getFunctionValue():Any? = max

    override fun preProcess(query: Query, value: Any?):Boolean {
        if(value != null && itemType == null)
            itemType = value.javaClass

        val valueDouble:Double = (value as? Number)?.toDouble() ?: 0.0
        val maxDouble:Double = (max as? Number)?.toDouble() ?: 0.0

        return valueLock.perform {
            if (valueDouble > maxDouble || max == null) {
                max = value
                return@perform true
            }
            return@perform false
        }
    }

}