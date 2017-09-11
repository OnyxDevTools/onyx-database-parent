package com.onyx.diskmap.store.impl

import com.onyx.diskmap.base.concurrent.AtomicCounter
import com.onyx.diskmap.base.concurrent.DefaultAtomicCounter
import com.onyx.diskmap.serializer.ObjectBuffer
import com.onyx.diskmap.serializer.ObjectSerializable
import com.onyx.diskmap.serializer.Serializers
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.async
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * The default implementation of a store that includes the i/o of writing to a basic file channel.
 * This is recommended for larger data sets.
 *
 * This class also encapsulates the serialization of objects that are read and written to the store.
 *
 * @since 1.0.0
 */
open class FileChannelStore() : Store {

    override var serializers: Serializers? = null // Legacy serializer uses this to get the serial version
    final override var filePath: String = ""
    var deleteOnClose: Boolean = false // Whether to delete this file upon closing database or JVM
    var bufferSliceSize = LARGE_FILE_SLICE_SIZE // Size of each slice

    protected var channel: FileChannel? = null
    protected var contextId:String? = null
    private var fileSizeCounter: AtomicCounter = DefaultAtomicCounter(0)

    constructor(filePath: String = "", context: SchemaContext? = null, deleteOnClose: Boolean = false) : this() {
        this.bufferSliceSize = if(deleteOnClose) SMALL_FILE_SLICE_SIZE else LARGE_FILE_SLICE_SIZE
        this.deleteOnClose = deleteOnClose
        this.filePath = filePath
        this.contextId = context?.contextId

        this.open(filePath = filePath)
        this.determineSize()
    }

    /**
     * Assign serializers based on what context id
     *
     * @param mapById Serializers with serializer id indexed
     * @param mapByName Serializers by class name
     */
    override fun assignSerializers(mapById: Map<Short, String>, mapByName: Map<String, Short>) {
        serializers = Serializers(mapById, mapByName, if (contextId == null) null else Contexts.get(contextId!!))
    }

    /**
     * Get the size of the file
     */
    override fun getFileSize():Long = fileSizeCounter.get()

    /**
     * Open the data file
     *
     * @param filePath Path of the file to open
     * @return Whether the file was opened or not
     */
    open fun open(filePath: String): Boolean {
        val file = File(filePath)
        try {
            // Create the data file if it does not exist
            if (!file.exists()) {
                file.parentFile.mkdirs()
                file.createNewFile()
            }

            // Open the random access file
            val randomAccessFile = RandomAccessFile(filePath, "rw")
            this.channel = randomAccessFile.channel
            this.fileSizeCounter.set(this.channel!!.size())

        } catch (e: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            return false
        }

        return channel!!.isOpen
    }

    /**
     * Set the size after opening a file.  The first 8 bytes are reserved for the size.  The reason why we maintain the size
     * outside of relying of the fileChannel is because it may not be accurate.  In order to force it's accuracy
     * we have to configure the file channel to do so.  That causes the store to be severely slowed down.
     */
    protected fun determineSize() {
        val buffer = this.read(0, 8)

        if (buffer == null || channel?.size() == 0L) {
            this.allocate(8)
        } else {
            val fSize = buffer.readLong()
            this.fileSizeCounter.set(fSize)
        }
    }

    /**
     * Close the data file
     *
     * @return Whether the file was closed successfully.
     */
    override fun close(): Boolean = try {
        if (!deleteOnClose) {
            this.channel!!.force(true)
        }
        this.channel!!.close()
        async {
            if (deleteOnClose) {
                delete()
            }
        }
        !this.channel!!.isOpen
    } catch (e: IOException) {
        false
    }

    /**
     * Commit all file writes
     */
    override fun commit() { this.channel?.force(true) }

    /**
     * Write an Object Buffer
     *
     * @param serializable Object buffer to write
     * @param position Position within the volume to write to.
     * @return How many bytes were written
     */
    override fun write(serializable: ObjectBuffer, position: Long): Int = channel!!.write(serializable.byteBuffer, position)

    /**
     * Write a serializable object to a volume.  This uses the ObjectBuffer for serialization
     *
     * @param serializable Object
     * @param position Position to write to
     */
    override fun write(serializable: ObjectSerializable, position: Long): Int {
        val objectBuffer = ObjectBuffer(serializers)
        serializable.writeObject(objectBuffer)
        return channel!!.write(objectBuffer.byteBuffer, position)
    }

    /**
     * Read a serializable object from the store
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param type class type
     * @return The object that was read from the store
     */
    override fun read(position: Long, size: Int, type: Class<*>): Any? {
        if (!validateFileSize(position)) return null

        val buffer = ObjectBuffer.allocate(size)

        channel!!.read(buffer, position)
        buffer.rewind()

        return if (ObjectSerializable::class.java.isAssignableFrom(type)) {
            val serializable = type.newInstance()
            val objectBuffer = ObjectBuffer(buffer, serializers)
            (serializable as ObjectSerializable).readObject(objectBuffer, position)
            serializable
        } else {
            ObjectBuffer.unwrap(buffer, serializers)
        }
    }

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializable object to read into
     * @return same object instance that was sent in.
     */
    override fun read(position: Long, size: Int, serializable: ObjectSerializable): Any? {
        if (!validateFileSize(position))
            return null

        val buffer = ObjectBuffer.allocate(size)

        channel!!.read(buffer, position)
        buffer.rewind()
        serializable.readObject(ObjectBuffer(buffer, serializers), position)

        return serializable
    }

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializerId Key to the serializer version that was used when written to the store
     * @return Object read from the store
     */
    override fun read(position: Long, size: Int, type: Class<*>, serializerId: Int): Any? {
        if (!validateFileSize(position))
            return null

        val buffer = ObjectBuffer.allocate(size)

        channel!!.read(buffer, position)
        buffer.rewind()

        return when {
            serializerId > 0 -> ObjectBuffer.unwrap(buffer, serializers, serializerId)
            ObjectSerializable::class.java.isAssignableFrom(type) -> {
                val serializable = type.newInstance()
                val objectBuffer = ObjectBuffer(buffer, serializers)
                (serializable as ObjectSerializable).readObject(objectBuffer, position)
                serializable
            }
            else -> ObjectBuffer.unwrap(buffer, serializers)
        }
    }

    /**
     * Write a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @return Object Buffer contains bytes read
     */
    override fun read(position: Long, size: Int): ObjectBuffer? {
        if (!validateFileSize(position))
            return null

        val buffer = ObjectBuffer.allocate(size)

        channel!!.read(buffer, position)
        buffer.rewind()

        return ObjectBuffer(buffer, serializers)

    }

    /**
     * Read the file channel and put it into a buffer at a position
     *
     * @param buffer   Buffer to put into
     * @param position position in store to read
     */
    override fun read(buffer: ByteBuffer, position: Long) {
        channel!!.read(buffer, position)
        buffer.flip()
    }

    /**
     * Validate we are not going to read beyond the allocated file storage.  This would be bad
     * @param position Position to validate
     * @return whether the object you seek is in a valid position
     */
    protected fun validateFileSize(position: Long): Boolean = position < fileSizeCounter.get()

    /**
     * Allocates a spot in the file
     *
     * @param size Allocate space within the store.
     * @return position of started allocated bytes
     */
    override fun allocate(size: Int): Long {
        val buffer = ObjectBuffer(serializers)
        val newFileSize = fileSizeCounter.getAndAdd(size)
        buffer.writeLong(newFileSize + size)

        this.write(buffer, 0)
        return newFileSize
    }

    /**
     * Delete File
     */
    override fun delete() {
        val dataFile = File(filePath)
        dataFile.delete()
    }

    /**
     * Reset the storage so that it has a clean slate
     * and truncates all relative data.
     *
     * @since 1.3.0
     */
    override fun reset() {
        fileSizeCounter.set(0)
        this.allocate(8)
    }

    companion object {
        val SMALL_FILE_SLICE_SIZE = 1024 * 256 // 256K
        val LARGE_FILE_SLICE_SIZE = 1024 * 1024 * 4 // 4MB
    }
}
