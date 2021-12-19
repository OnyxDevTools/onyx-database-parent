package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType
import java.util.*

/**
 * Convert to lowercase string
 */
class LowerQueryFunction(attribute:String) : BaseQueryFunction(attribute, QueryFunctionType.LOWER), QueryFunction {

    override fun newInstance(): QueryFunction = LowerQueryFunction(attribute)

    override fun execute(value: Any?): Any = value.toString().lowercase(Locale.getDefault())

}