package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType

/**
 * String replace query function
 */
class ReplaceQueryFunction(attribute:String = "", private val param1:String? = null, private val param2: String? = null) : BaseQueryFunction(attribute, QueryFunctionType.REPLACE), QueryFunction {
    override fun execute(value: Any?): Any? = value?.toString()?.replace(param1?.toRegex() ?: "".toRegex(), param2 ?: "null")
    override fun newInstance(): QueryFunction = ReplaceQueryFunction(attribute, param1, param2)
}