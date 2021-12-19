package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType
import java.util.*

/**
 * Query function that upper cases a value
 */
class UpperQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.UPPER), QueryFunction {
    override fun execute(value: Any?): Any = value.toString().uppercase(Locale.getDefault())
    override fun newInstance(): QueryFunction = UpperQueryFunction(attribute)
}