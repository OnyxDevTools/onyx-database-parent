package transaction

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.persistence.query.Query
import java.nio.ByteBuffer

internal fun serializedWalRecord(firstRow: Int, partition: String = ""): ByteArray {
    val payload = BufferStream.toBuffer(Query().apply {
        this.firstRow = firstRow
        this.partition = partition
    })
    try {
        return ByteBuffer.allocate(5 + payload.remaining())
            .put(3.toByte()) // DELETE_QUERY
            .putInt(payload.remaining())
            .put(payload)
            .array()
    } finally {
        BufferPool.recycle(payload)
    }
}
