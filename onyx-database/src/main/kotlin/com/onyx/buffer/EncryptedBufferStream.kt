package com.onyx.buffer

import com.onyx.extension.common.instance
import com.onyx.extension.withBuffer
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.context.SchemaContext
import java.nio.ByteBuffer

/**
 * @author Tim Osborn
 * @since 2.2.0
 *
 * It overrides the default implementation to encrypt and decrypt the managed entities.
 */
class EncryptedBufferStream(buffer: ByteBuffer) : BufferStream(buffer) {

    constructor() : this(BufferPool.allocateAndLimit(ExpandableByteBuffer.BUFFER_ALLOCATION))

    /**
     * Put a managed entity.  Encrypt the buffer before putting it.
     *
     * @param entity Entity to serialize in an encrypted format
     * @param context Needed to get encryption method and entity metadata
     * @since 2.2.0
     */
    override fun putEntity(entity: ManagedEntity, context: SchemaContext?) {
        val bufferToEncrypt = BufferStream()
        entity.write(bufferToEncrypt, context)
        val encryptedBuffer = (bufferToEncrypt.byteBuffer.flip() as ByteBuffer).encrypt(context)
        putInt(encryptedBuffer.remaining())
        expandableByteBuffer!!.ensureSize(encryptedBuffer.remaining())
        expandableByteBuffer!!.buffer.put(encryptedBuffer)
    }

    /**
     * Returns the decrypted entity
     *
     * @return entity in a decrypted format
     * @since 2.2.0
     */
    override val entity: ManagedEntity
        get() {
            val bufferLength = int
            val encryptedByteArray = ByteArray(bufferLength)
            expandableByteBuffer!!.buffer.get(encryptedByteArray)
            val decryptedByteBuffer = ByteBuffer.wrap(encryptedByteArray).decrypt(context)
            val encryptedBufferStream = BufferStream(decryptedByteBuffer)
            val serializerId = encryptedBufferStream.expandableByteBuffer!!.buffer.int
            encryptedBufferStream.expandableByteBuffer!!.buffer.position(encryptedBufferStream.expandableByteBuffer!!.buffer.position() - Integer.BYTES)
            val systemEntity = context!!.getSystemEntityById(serializerId)
            val entity: ManagedEntity = systemEntity!!.type.instance()
            entity.read(encryptedBufferStream, context)
            return entity
        }

    /**
     * Takes an encrypted entity and formats it into a key value map
     *
     * @param context Schema context used for decryption
     * @return Key value map of decrypted entity
     * @since 2.2.0
     *
     */
    override fun toMap(context: SchemaContext): Map<String, Any?> {
        byte
        int
        val results = HashMap<String, Any?>()

        withBuffer(this.expandableByteBuffer!!.buffer) {
            this.expandableByteBuffer!!.buffer = this.expandableByteBuffer!!.buffer.decrypt(context)
            val systemEntity = context.getSystemEntityById(int)!!
            for ((name) in systemEntity.attributes) results[name] = value
        }
        return results
    }

}

/**
 * Extension Method to encrypt a byte buffer
 *
 * @param context Schema context
 * @return Encrypted byte buffer
 *
 * @since 2.2.0
 */
fun ByteBuffer.encrypt(context: SchemaContext?): ByteBuffer {
    context?.encryption ?: return this
    val bytes = ByteArray(this.remaining())
    get(bytes)
    BufferPool.recycle(this)
    val encryptedBytes = context.encryption!!.encrypt(bytes)!!
    val newBuffer = BufferPool.allocateAndLimit(encryptedBytes.size)
    newBuffer.put(encryptedBytes)
    newBuffer.rewind()
    return newBuffer
}


/**
 * Extension Method to decrypt a byte buffer
 *
 * @param context Schema context
 * @return Decrypted byte buffer
 *
 * @since 2.2.0
 */
fun ByteBuffer.decrypt(context: SchemaContext?): ByteBuffer {
    context?.encryption ?: return this
    val bytes = ByteArray(this.remaining())
    get(bytes)
    BufferPool.recycle(this)
    val decryptedBytes = context.encryption!!.decrypt(bytes)
    val newBuffer = BufferPool.allocateAndLimit(decryptedBytes.size)
    newBuffer.put(decryptedBytes)
    newBuffer.rewind()
    return newBuffer
}
