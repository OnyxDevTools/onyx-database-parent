package com.onyx.diskmap.factory.impl

import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.impl.DiskSkipListMap
import com.onyx.diskmap.store.*
import com.onyx.diskmap.store.impl.*
import com.onyx.extension.common.ClassMetadata.classForName
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.context.SchemaContext

import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by timothy.osborn on 3/25/15.
 *
 *
 * This class is responsible for building all maps.  There is one map factory per file and or storage
 */
@Suppress("UNCHECKED_CAST")
open class DefaultDiskMapFactory : DiskMapFactory {

    /**
     * Get the file store in order to re-attach a data structure
     *
     * @return The store the map factory uses
     */
    open protected lateinit var store: Store

    // Contains all initialized maps
    open protected val maps: MutableMap<String, Map<*, *>> = OptimisticLockingMap(WeakHashMap())

    // Contains all initialized maps
    open protected val mapsByHeader = OptimisticLockingMap(WeakHashMap<Header, Map<*, *>>())

    // Internal map that runs on storage
    open protected var internalMaps: MutableMap<String, Long>

    // region constructors

    /**
     * Constructor
     *
     * @param filePath Where the file is located to store the maps
     * @since 1.0.0
     */
    constructor(filePath: String) : this(filePath, StoreType.FILE, null)

    /**
     * Constructor
     *
     * @param filePath Where the location of the factory feeds off of
     * @param type     Type of storage mechanism
     *
     * @since 1.0.0
     */
    @Suppress("unused")
    constructor(filePath: String, type: StoreType) : this(filePath, type, null)

    /**
     * Constructor for a database
     *
     * @param filePath File path to hold the disk structure data
     * @param type     Storage type
     * @param context  Database Schema context
     * @since 1.0.0
     */
    constructor(filePath: String, type: StoreType, context: SchemaContext?) : this("", filePath, type, context, false)

    /**
     * Constructor
     *
     * @param filePath File path to hold the disk structure data
     * @since 1.0.0
     */
    constructor(
        fileSystemPath: String?,
        filePath: String,
        type: StoreType,
        context: SchemaContext?,
        deleteOnClose: Boolean,
        predefinedStore: Store? = null
    ) {
        val path: String =
            if (fileSystemPath == null || fileSystemPath == "") filePath else fileSystemPath + File.separator + filePath

        if (predefinedStore != null) {
            this.store = predefinedStore
        } else {
            when {
                type === StoreType.MEMORY_MAPPED_FILE && isMemMapSupported -> {
                    this.store = if (context?.encryptDatabase == true) EncryptedMemoryMappedStore(
                        path,
                        context,
                        deleteOnClose
                    ) else MemoryMappedStore(path, context, deleteOnClose)
                }

                type === StoreType.FILE || type === StoreType.MEMORY_MAPPED_FILE && !isMemMapSupported -> {
                    this.store = if (context?.encryptDatabase == true) EncryptedFileChannelStore(
                        path,
                        context,
                        deleteOnClose
                    ) else FileChannelStore(path, context, deleteOnClose)
                }

                type === StoreType.IN_MEMORY -> {
                    val storeId = storeIdCounter.incrementAndGet().toString()
                    this.store = InMemoryStore(context, storeId)
                }
            }
        }

        // Create default maps.  These locations indicate the spacing between initial map allocations.  In this case,
        // it indicates a load factor map with a load factor of 1.  If the allocation changes such as the header
        // or the default allocation within the maps' constructors and/or the serializer map types, this will need
        // to be changed.

        internalMaps = if (store.getFileSize() == FIRST_HEADER_LOCATION) {
            getHashMap(String::class.java, newMapHeader())
        } else {
            getHashMap(
                String::class.java,
                (store.read(FIRST_HEADER_LOCATION, Header.HEADER_SIZE, Header()) as Header?)!!
            )
        }
    }

    // endregion

    // region Header Allocation

    /**
     * Create a new Map reference header
     * @return the created header ripe for instantiating a new map
     *
     * @since 1.2.0
     */
    override fun newMapHeader(): Header {
        val header = Header()
        header.position = store.allocate(Header.HEADER_SIZE)
        store.write(header, header.position)
        return header
    }

    // endregion

    // region Store Methods

    /**
     * Close the file stores
     */
    override fun close() = store.close()

    /**
     * Force the storage to persist
     */
    override fun commit() = store.commit()

    /**
     * Delete file
     */
    override fun delete() {
        this.close()
        this.store.delete()
    }

    /**
     * Reset the map factory.  This was added so that the map builders and
     * storage can be recycled and re-used.  The reason for that is there
     * was a large performance hit on destroying direct byte buffers.
     * That is no longer the case since they do not get destroyed.
     *
     * @since 1.3.0
     */
    override fun reset() {
        // Reset the file size
        store.reset()

        internalMaps = if (store.getFileSize() == FIRST_HEADER_LOCATION) {
            getHashMap(String::class.java, newMapHeader())
        } else {
            getHashMap(
                String::class.java,
                (store.read(FIRST_HEADER_LOCATION, Header.HEADER_SIZE, Header()) as Header?)!!
            )
        }
    }

    // endregion

    // region Builders

    /**
     * Get the instance of a map.
     *
     * @param name       Name of the map to uniquely identify it
     *
     * @return Instantiated map with storage
     * @since 1.1.0
     *
     * Note, this was changed to use what was being referred to as a DefaultDiskMap which was a parent of AbstractBitmap.
     * It is now an implementation of an inter-changeable index followed by a skip list.
     */
    override fun <T : Map<*, *>> getHashMap(keyType: Class<*>, name: String): T = getMapWithType(keyType, name)

    /**
     * Get Disk Map with the ability to dynamically change the load factor.  Meaning change how it scales dynamically
     *
     * @param header     reference within storage
     * @return Instantiated disk structure
     *
     * @since 1.0.0
     */
    override fun <T : Map<*, *>> getHashMap(keyType: Class<*>, header: Header): T = mapsByHeader.getOrPut(header) {
        DiskSkipListMap<Any, Any>(
            WeakReference(store), header, keyType
        )
    } as T

    /**
     * Default Map factory.  This creates or gets a map based on the name and puts it into a map
     *
     * @param name identifier of a map
     * @return Created map
     *
     * @since 1.2.0
     */
    open protected fun <T : Map<*, *>> getMapWithType(keyType: Class<*>, name: String): T = maps.getOrPut(name) {
        var header: Header? = null
        val headerReference = internalMaps[name]
        if (headerReference != null)
            header = store.read(headerReference, Header.HEADER_SIZE, Header()) as Header?

        // Create a new header for the new structure we are creating
        if (header == null) {
            header = Header()
            header.position = store.allocate(Header.HEADER_SIZE)
            store.write(header, header.position)
            internalMaps[name] = header.position
        }

        return@getOrPut DiskSkipListMap<Any, Any>(WeakReference(store), header, keyType)
    } as T

    // endregion

    companion object {

        private val storeIdCounter = AtomicInteger(0)
        private const val FIRST_HEADER_LOCATION = 8L

        /**
         * Check if large files can be mapped into memory.
         * For example 32bit JVM can only address 2GB and large files can not be mapped,
         * so for 32bit JVM this function returns false.
         *
         * @since 1.2.2 Add additional check to ensure DirectBuffers exist
         */
        private val isMemMapSupported: Boolean by lazy {
            val prop = System.getProperty("os.arch")
            return@lazy try {
                @Suppress("SENSELESS_COMPARISON")
                prop != null && prop.contains("64") && classForName("sun.nio.ch.DirectBuffer") != null
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

    /**
     * In order to purge memory after long-running intensive tasks, this method has been added.  It will
     * clear non-volatile cached items in the disk maps
     */
    override fun flush() {
        maps.clear()
        mapsByHeader.clear()
    }
}
