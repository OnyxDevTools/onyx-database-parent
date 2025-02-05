package com.onyx.persistence.query

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.SchemaContext
import java.io.Serializable

/**
 * Created by Tim Osborn on 3/13/16.
 *
 * The purpose of this class is to be a wrapper for the results since we need the result count
 * and the results.  Using the embedded, the reference of resultCount is used to update the query value
 * but since we do not have that luxury when dealing with a remote server we need this wrapper.
 */
class QueryResult @JvmOverloads constructor(var query: Query? = null, var results: Any? = null) : Serializable, BufferStreamable {

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        query = buffer.value as Query
        results = buffer.value
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putObject(query)
        buffer.putObject(results)
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        this.read(buffer)
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) {
        this.write(buffer)
    }
}
