package pojo

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.SchemaContext

/**
 * Created by Tim Osborn on 7/31/16.
 */
class BufferStreamableObject : BufferStreamable {
    var myString: String? = null
    var myInt: Int = 0
    var simple: Simple? = null

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        myString = buffer.string
        myInt = buffer.int
        simple = buffer.value as Simple
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putString(myString!!)
        buffer.putInt(myInt)
        buffer.putObject(simple)
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        myString = buffer.string
        myInt = buffer.int
        simple = buffer.value as Simple
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) {
        buffer.putString(myString!!)
        buffer.putInt(myInt)
        buffer.putObject(simple)
    }
}
