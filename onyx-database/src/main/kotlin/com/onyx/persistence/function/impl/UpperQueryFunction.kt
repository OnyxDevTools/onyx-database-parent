package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType

/**
 * Query function that upper cases a value
 */
class UpperQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.UPPER), QueryFunction {
    override fun execute(value: Any?): Any? = value.toString().toUpperCase()
    override fun newInstance(): QueryFunction = UpperQueryFunction(attribute)
}