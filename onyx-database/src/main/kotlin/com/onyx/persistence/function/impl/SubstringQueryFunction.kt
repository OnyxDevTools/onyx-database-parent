package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType

/**
 * Query function that performs a substring
 */
class SubstringQueryFunction(attribute:String = "", private val param1:String? = null, private val param2: String? = null) : BaseQueryFunction(attribute, QueryFunctionType.SUBSTRING), QueryFunction {

    override fun execute(value: Any?): Any? {
        val toStringValue = value?.toString()
        return toStringValue?.substring(if(toStringValue.length > (param1?.toInt() ?: 0)) (param1?.toInt() ?: 0) else toStringValue.length, if(toStringValue.length > (param2?.toInt() ?: 0)) (param2?.toInt() ?: 0) else toStringValue.length )
    }

    override fun newInstance(): QueryFunction = SubstringQueryFunction(attribute, param1, param2)

}