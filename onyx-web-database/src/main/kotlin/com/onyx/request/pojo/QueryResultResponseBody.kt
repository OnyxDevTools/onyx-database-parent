package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 4/13/15.
 *
 * POJO for query result
 */
class QueryResultResponseBody constructor() : BufferStreamable {

    var maxResults: Int = 0
    var resultList: MutableList<Any> = ArrayList()
    var results: Int = 0

    constructor(maxResults: Int, results: Int):this() {
        this.maxResults = maxResults
        this.results = results
    }

    constructor(maxResults: Int, results: MutableList<Any>):this() {
        this.maxResults = maxResults
        this.resultList = results
    }
}
