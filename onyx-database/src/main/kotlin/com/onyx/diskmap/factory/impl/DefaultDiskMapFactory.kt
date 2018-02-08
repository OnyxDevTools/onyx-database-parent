package com.onyx.diskmap.factory.impl

import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.impl.DiskHashMap
import com.onyx.diskmap.impl.DiskMatrixHashMap
import com.onyx.diskmap.store.*
import com.onyx.diskmap.store.impl.FileChannelStore
import com.onyx.diskmap.store.impl.InMemoryStore
import com.onyx.diskmap.store.impl.MemoryMappedStore
import com.onyx.extension.common.ClassMetadata.classForName
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.context.SchemaContext

import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by timothy.osborn on 3/25/15.
 *
 *
 * This class is responsible for building all maps.  There is one map factory per file and or storage
 */
@Suppress("UNCHECKED_CAST")
class DefaultDiskMapFactory : DiskMapFactory {

    /**
     * Get the file store in order to re-attach a data structure
     *
     * @return The store the map factory uses
     */
    private lateinit var store: Store

    // Contains all initialized maps
    private val maps: MutableMap<String, Map<*,*>> = Collections.synchronizedMap(WeakHashMap())

    // Contains all initialized maps
    private val mapsByHeader = OptimisticLockingMap(WeakHashMap<Header, Map<*, *>>())

    // Internal map that runs on storage
    private var internalMaps: MutableMap<String, Long>

    private var canStoreKeyInNode:Boolean = false

    private var version:Int = 0

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
    constructor(fileSystemPath: String?, filePath: String, type: StoreType, context: SchemaContext?, deleteOnClose: Boolean) {
        val path: String =  if (fileSystemPath == null || fileSystemPath == "") filePath else fileSystemPath + File.separator + filePath

        val isNew = !File(path).exists()

        when {
            type === StoreType.MEMORY_MAPPED_FILE && isMemMapSupported ->
                this.store = MemoryMappedStore(path, context, deleteOnClose)
            type === StoreType.FILE || type === StoreType.MEMORY_MAPPED_FILE && !isMemMapSupported ->
                this.store = FileChannelStore(path, context, deleteOnClose)
            type === StoreType.IN_MEMORY -> {
                val storeId = storeIdCounter.incrementAndGet().toString()
                this.store = InMemoryStore(context, storeId)
            }
        }

        // Create default maps.  These locations indicate the spacing between initial map allocations.  In this case,
        // it indicates a load factor map with a load factor of 1.  If the allocation changes such as the header
        // or the default allocation within the maps' constructors and/or the serializer map types, this will need
        // to be changed.

        internalMaps = if (store.getFileSize() == FIRST_HEADER_LOCATION) {
            newScalableMap(String::class.java, this.store, newMapHeader(), 1)
        } else {
            getHashMap(String::class.java, (store.read(FIRST_HEADER_LOCATION, Header.HEADER_SIZE, Header()) as Header?)!!, 1)
        }

        determineKeyStore(isNew)
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
            newScalableMap(String::class.java, this.store, newMapHeader(), 1)
        } else {
            getHashMap(String::class.java, (store.read(FIRST_HEADER_LOCATION, Header.HEADER_SIZE, Header()) as Header?)!!, 1)
        }
    }

    // endregion

    // region Builders

    /**
     * Create a new Disk Map.  This uses both a skip list and a Bitmap index
     *
     * @param store      File Storage
     * @param header     Reference Node
     * @param loadFactor Value from 1-10.
     *
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     * @return Instantiated disk map
     */
    private fun <T : Map<*,*>> newScalableMap(keyType:Class<*>, store: Store, header: Header, loadFactor: Int):T = if (loadFactor < 5) DiskHashMap<Any, Any?>(store, header, loadFactor, keyType, canStoreKeyInNode) as T else DiskMatrixHashMap<Any, Any?>(store, header, loadFactor, keyType, canStoreKeyInNode) as T

    /**
     * Create a hash map with a given header.  This should not be invoked unless it is used to grab a stateless
     * instance of a disk map.  Stateless meaning, the header has already been setup.  Note, this is not thread safe
     * If you were to invoke this with the same header and use the maps concurrently you WILL corrupt your data.
     * You must use alternative means of thread safety.
     *
     * Since this implementation is stateless, it does not provide caching nor thread safety.
     *
     * @param header     Head of the disk map
     * @param loadFactor Load factor in which the map was instantiated with.
     * @return Stateless instance of a disk map
     *
     * @since 1.2.0
     */
    override fun <T : Map<*,*>> newHashMap(keyType:Class<*>, header: Header, loadFactor: Int): T = DiskHashMap<Any, Any?>(store, header, loadFactor, false, keyType, canStoreKeyInNode) as T

    /**
     * Get the instance of a map.  Based on the loadFactor it may be a multi map with a hash index followed by a skip list
     * or a multi map with a hash matrix followed by a skip list.
     *
     * @param name       Name of the map to uniquely identify it
     *
     * @param loadFactor Value from 1-10.
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated map with storage
     * @since 1.1.0
     *
     * Note, this was changed to use what was being referred to as a DefaultDiskMap which was a parent of AbstractBitmap.
     * It is now an implementation of an inter-changeable index followed by a skip list.
     */
    override fun <T : Map<*,*>> getHashMap(keyType:Class<*>, name: String, loadFactor: Int): T = getMapWithType(keyType, name, loadFactor)

    /**
     * Get Disk Map with the ability to dynamically change the load factor.  Meaning change how it scales dynamically
     *
     * @param header     reference within storage
     *
     *
     * @param loadFactor Value from 1-10.
     *
     * The load factor value is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @return Instantiated disk structure
     *
     * @since 1.0.0
     */
    override fun <T : Map<*,*>> getHashMap(keyType:Class<*>, header: Header, loadFactor: Int): T = mapsByHeader.getOrPut(header) { newScalableMap<T>(keyType, store, header, loadFactor) } as T

    /**
     * Default Map factory.  This creates or gets a map based on the name and puts it into a map
     *
     * @param name identifier of a map
     * @return Created map
     *
     * @since 1.2.0
     */
    private fun <T : Map<*,*>> getMapWithType(keyType:Class<*>, name: String, loadFactor: Int): T = maps.getOrPut(name) {
        var header: Header? = null
        val headerReference = internalMaps[name]
        if (headerReference != null)
            header = store.read(headerReference, Header.HEADER_SIZE, Header()) as Header?

        // Create a new header for the new structure we are creating
        if (header == null) {
            header = Header()
            header.position = store.allocate(Header.HEADER_SIZE)
            store.write(header, header.position)
            internalMaps.put(name, header.position)
        }

        return@getOrPut newScalableMap(keyType, store, header, loadFactor)
    } as T

    // endregion

    /**
     * This method determines whether we can store the key value within the key node rather than somewhere outside the
     * store.  If the file is new or was created after this functionality was put in place, the app will support storing
     * the key within the SkipNode.
     *
     * @since 2.1.3 Improve performance
     * @return Whether the store supports storing the key within the skip node
     */
    private fun determineKeyStore(isNew:Boolean) {
        version = internalMaps.getOrPut("_VERSION_") { if(isNew) 1L else 0L }.toInt()
        canStoreKeyInNode = (version > 0)
    }

    override fun getDefaultLoadFactor(): Int = if(version > 0) 2 else 1

    companion object {

        private val storeIdCounter = AtomicInteger(0)
        private val FIRST_HEADER_LOCATION = 8L

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
}
