package com.onyx.persistence.function

import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryFunctionType

/**
 * This contract represents the lifecycle of a query function.
 */
interface QueryFunction {

    /**
     * Type of query function
     */
    var type:QueryFunctionType

    /**
     * Attribute the function represents
     */
    var attribute:String

    /**
     * Get the end result of the query function.  This is invoked in the event the function is a post processed
     * function that required an aggregation of the entire result.
     *
     * @since 2.1.3
     */
    fun getFunctionValue(): Any? = null

    /**
     * Tell the function to aggregate with a value
     *
     * @since 2.1.3
     * @return Whether the collector needs to process the rest of the selections.  This is true in the event of
     *         a min or a max function call.  The collector needs to get the values for that matching row.
     */
    fun preProcess(query: Query, value: Any?): Boolean = false

    /**
     * Aggregate the results if needed
     *
     * @since 2.1.3
     */
    fun postProcess(query: Query) {  }

    /**
     * This is used to invoke a pure selection function.
     */
    fun execute(value:Any?):Any? = Unit

    /**
     * Create a new instance of the same type of function
     */
    fun newInstance():QueryFunction

}