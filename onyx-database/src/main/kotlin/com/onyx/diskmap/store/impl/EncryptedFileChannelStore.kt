package com.onyx.diskmap.store.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.buffer.EncryptedBufferStream
import com.onyx.persistence.context.SchemaContext

/**
 * @author Tim Osborn
 *
 * Encrypted File Channel storage file
 *
 * @since 2.2.0 Added for encrypted storage support
 */
class EncryptedFileChannelStore(filePath: String, context: SchemaContext, deleteOnClose: Boolean) : FileChannelStore(filePath, context, deleteOnClose) {

    /**
     * Retrieved encrypted object.  This is only for managed entities
     *
     * @return Decrypted managed entity
     * @since 2.2.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getObject(position: Long):T {
        val size = BufferPool.withIntBuffer {
            this.read(it, position)
            it.rewind()
            it.int
        }
        if (size == 0) return null as T

        BufferPool.allocateAndLimit(size) {
            this.read(it, position + Integer.BYTES)
            it.rewind()
            @Suppress("UNCHECKED_CAST")
            return EncryptedBufferStream(it).getObject(context) as T
        }
    }

    /**
     * Write a managed entity in an encrypted format
     *
     * @param value entity to write
     * @return Record id of entity
     * @since 2.2.0
     */
    override fun writeObject(value: Any?): Long {
        val stream = EncryptedBufferStream()
        try {
            stream.byteBuffer.position(Integer.BYTES)
            stream.putObject(value, context)
            stream.flip()
            val valueBuffer = stream.byteBuffer
            valueBuffer.putInt(0, valueBuffer.limit() - Integer.BYTES)
            val position = allocateObject(valueBuffer.remaining())
            write(valueBuffer, position)
            return position
        } finally {
            stream.recycle()
        }
    }

    /**
     * Read an encrypted entity's data
     *
     * @since 2.2.0
     * @return Streamable buffer
     */
    override fun readObject(position: Long, size: Int): BufferStream {
        val buffer = read(position, size)
        return EncryptedBufferStream(buffer!!.byteBuffer)
    }
}
