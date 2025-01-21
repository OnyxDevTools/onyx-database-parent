package com.onyx.persistence.function

import com.onyx.persistence.function.impl.*
import com.onyx.persistence.query.QueryFunctionType

object QueryFunctionFactory {

    fun create(type:QueryFunctionType, attribute:String, param1:String? = null, param2:String? = null):QueryFunction = when(type) {
        QueryFunctionType.SUM -> SumQueryFunction(attribute)
        QueryFunctionType.MIN -> MinQueryFunction(attribute)
        QueryFunctionType.MAX -> MaxQueryFunction(attribute)
        QueryFunctionType.AVG -> AvgQueryFunction(attribute)
        QueryFunctionType.STD -> StdDevQueryFunction(attribute)
        QueryFunctionType.MEDIAN -> MedianQueryFunction(attribute)
        QueryFunctionType.VARIANCE -> VarianceQueryFunction(attribute)
        QueryFunctionType.COUNT -> CountQueryFunction(attribute)
        QueryFunctionType.UPPER -> UpperQueryFunction(attribute)
        QueryFunctionType.LOWER -> LowerQueryFunction(attribute)
        QueryFunctionType.REPLACE -> ReplaceQueryFunction(attribute, param1, param2)
        QueryFunctionType.SUBSTRING -> SubstringQueryFunction(attribute, param1, param2)
    }
}
