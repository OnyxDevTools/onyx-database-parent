package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 4/13/15.
 *
 * POJO for query result
 */
class QueryResultResponseBody constructor() : BufferStreamable {

    @Suppress("MemberVisibilityCanPrivate")
    var maxResults: Int = 0
    var results: MutableList<Any> = ArrayList()

    constructor(maxResults: Int, results: MutableList<Any>):this() {
        this.maxResults = maxResults
        this.results = results
    }
}
