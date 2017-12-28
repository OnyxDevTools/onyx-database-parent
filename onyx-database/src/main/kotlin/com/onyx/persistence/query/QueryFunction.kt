package com.onyx.persistence.query

import com.onyx.buffer.BufferStreamable

class QueryFunction @JvmOverloads constructor(var type: QueryFunctionType = QueryFunctionType.COUNT, var attribute:String = "", private var param1:String? = null, private var param2: String? = null) : BufferStreamable {

    /**
     * Execute a selection function.
     *
     * @since 2.1.0
     */
    fun execute(value:Any?):Any? = when(type) {
        QueryFunctionType.LOWER -> value?.toString()?.toLowerCase()
        QueryFunctionType.UPPER -> value?.toString()?.toUpperCase()
        QueryFunctionType.REPLACE -> value?.toString()?.replace(param1?.toRegex() ?: "".toRegex(), param2 ?: "null")
        QueryFunctionType.SUBSTRING -> {
            val toStringValue = value?.toString()
            toStringValue?.substring(if(toStringValue.length > (param1?.toInt() ?: 0)) (param1?.toInt() ?: 0) else toStringValue.length, if(toStringValue.length > (param2?.toInt() ?: 0)) (param2?.toInt() ?: 0) else toStringValue.length )
        }
        else -> value
    }
}
