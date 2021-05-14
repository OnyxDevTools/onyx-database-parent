package com.onyx.persistence.function.impl

import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType
import java.util.concurrent.atomic.AtomicInteger

/**
 * Function used to count the number of matching results.  Use the distinct key word to differentiate
 * duplicate entries.
 */
class CountQueryFunction(attribute:String = "") : BaseQueryFunction(attribute, QueryFunctionType.COUNT), QueryFunction {

    override fun newInstance(): QueryFunction = CountQueryFunction(attribute)

    private val count: AtomicInteger = AtomicInteger(0)
    private val uniqueValues:MutableSet<Any?> by lazy { HashSet() }
    private val valueLock = DefaultClosureLock()
    override fun getFunctionValue():Int = count.get()

    override fun preProcess(query: Query, value: Any?): Boolean {
        if(query.isDistinct ) {
            valueLock.perform {
                uniqueValues.add(value)
            }
        }
        else
            count.incrementAndGet()
        return false
    }

    override fun postProcess(query: Query) {
        if(query.isDistinct)
            count.set(uniqueValues.size)
    }

}