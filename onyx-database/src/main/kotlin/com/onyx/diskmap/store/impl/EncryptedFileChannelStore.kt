package com.onyx.diskmap.store.impl

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.buffer.EncryptedBufferStream
import com.onyx.extension.withBuffer
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
    override fun <T> getObject(position: Int):T {
        val size = BufferPool.withIntBuffer {
            this.read(it, position)
            it.rewind()
            it.int
        }

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
    override fun writeObject(value: Any?): Int {
        val stream = EncryptedBufferStream()
        stream.putObject(value, context)
        stream.flip()

        return withBuffer(stream.byteBuffer) { valueBuffer ->
            val size = valueBuffer.limit()
            val position = this.allocate(size + Integer.BYTES)

            BufferPool.withIntBuffer {
                it.putInt(size)
                it.rewind()
                this.write(it, position)
            }

            this.write(valueBuffer, position + Integer.BYTES)

            return@withBuffer position
        }
    }

    /**
     * Read an encrypted entity's data
     *
     * @since 2.2.0
     * @return Streamable buffer
     */
    override fun readObject(position: Int, size: Int): BufferStream {
        val buffer = read(position, size)
        return EncryptedBufferStream(buffer!!.byteBuffer)
    }
}
