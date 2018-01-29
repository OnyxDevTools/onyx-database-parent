package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType

abstract class BaseQueryFunction(override var attribute:String, override var type: QueryFunctionType) : QueryFunction