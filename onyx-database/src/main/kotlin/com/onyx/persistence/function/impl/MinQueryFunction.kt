package com.onyx.persistence.function.impl

import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType
import java.util.*

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

        val valueDouble:Float =  if(value is Date) value.time.toFloat() else (value as? Number)?.toFloat() ?: 0.0f
        val minDouble:Float =  if(min is Date) (min as Date).time.toFloat() else (min as? Number)?.toFloat() ?: 0.0f

        return valueLock.perform {
            if (valueDouble < minDouble || min == null) {
                min = value
                return@perform true
            }
            return@perform false
        }
    }

}