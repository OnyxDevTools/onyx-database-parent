package com.onyx.buffer

import com.onyx.exception.OnyxException
import com.onyx.persistence.context.SchemaContext
import com.onyx.exception.BufferingException
import com.onyx.extension.common.*
import com.onyx.lang.SortedList
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity

import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Created by Tim Osborn on 7/2/16.
 *
 *
 * The expandableByteBuffer expandableByteBuffer is an value serialization expandableByteBuffer.   It is inspired by the InputStream and and OutputStream
 * and how it interacts with the Externalized interface.  An exception to that is that the underlying io is to
 * a ByteBuffer.
 */
open class BufferStream(buffer: ByteBuffer) {

    // region Properties

    protected var context: SchemaContext? = null

    val metadata by lazy {
        metadata(contextId = context?.contextId ?: "")
    }

    // Number of references to retain the index number of the said reference
    private var referenceCount = 0

    // Wrapper for a ByteBuffer to retain reference
    var expandableByteBuffer: ExpandableByteBuffer? = ExpandableByteBuffer(buffer)

    // Indicates whether we are pulling from the expandableByteBuffer or putting into the expandableByteBuffer.
    private var isComingFromBuffer = false

    // References by class and value hash.
    private val references = HashMap<Class<*>, HashMap<Any, Int>>()

    // References by index number ordered by first used
    private val referencesByIndex = HashMap<Int, Any>()

    /**
     * Getter for underlying byte buffer
     * @return The underlying byte buffer with the goods
     */
    val byteBuffer: ByteBuffer
        get() = expandableByteBuffer!!.buffer

    // endregion

    // region Constructors

    /**
     * Constructor with underlying byte buffer
     * @param size Size of buffer to allocate
     */
    constructor(size: Int) : this(BufferPool.allocateAndLimit(size))

    /**
     * Default constructor with no buffer
     */
    private constructor(context: SchemaContext?) : this(BufferPool.allocate(ExpandableByteBuffer.BUFFER_ALLOCATION)) {
        this.context = context
    }

    /**
     * Default constructor with no buffer
     */
    constructor() : this(BufferPool.allocate(ExpandableByteBuffer.BUFFER_ALLOCATION))

    // endregion

    // region Reference Tracking

    /**
     * Add a reference by class and sequential order by which it was used
     *
     * @param reference Object reference
     */
    private fun addReference(reference: Any) {
        // If we are pulling from the expandableByteBuffer there is no reason to maintain a hash structure of the value references
        // especially since they are not fully hydrated and may not have valid hashes yet.
        if (isComingFromBuffer) {
            referenceCount++
            referencesByIndex[referenceCount] = reference
        } else {
            references.getOrPut(reference.javaClass) { HashMap() }
                    .getOrPut(reference) {
                        referenceCount++
                        referencesByIndex[referenceCount] = reference
                        referenceCount
                    }
        }
    }

    /**
     * Get the reference index of an value
     * @param reference Reference of an value
     * @return if it exists it will return the index number otherwise -1
     */
    private fun referenceIndex(reference: Any?): Int {
        if (reference == null)
            return -1

        val classMap = references[reference.javaClass] ?: return -1
        return classMap[reference] ?: return -1
    }

    /**
     * Reference of the reference index
     * @param index Index to seek to
     * @return The actual value referenced
     */
    private fun referenceOf(index: Int): Any = referencesByIndex[index]!!

    //endregion

    //region Cleanup

    /**
     * Recycle the underlying byte buffer in order to prevent un-necessary re-allocation of byte buffers
     *
     */
    fun recycle() {
        clear()
        BufferPool.recycle(expandableByteBuffer!!.buffer)
        expandableByteBuffer = null
    }

    /**
     * Clear byte buffer and references
     */
    fun clear() {
        this.expandableByteBuffer!!.buffer.clear()
        clearReferences()
    }

    /**
     * Clear references so that it forces the re-serialization of objects rather than inserting reference
     * placeholders into the buffer.
     *
     * @since 2.0.0
     */
    private fun clearReferences() {
        references.clear()
        referencesByIndex.clear()
        referenceCount = 0
    }

    /**
     * Flip the underlying byte buffer
     */
    fun flip() {
        this.expandableByteBuffer!!.buffer.flip()
    }

    // endregion

    // region Read Buffer

    /**
     * Get Long from the buffer
     *
     * @since  1.1.0
     * @return long read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val long: Long
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Long.BYTES)
            return expandableByteBuffer!!.buffer.long
        }

    /**
     * Get int from the buffer
     *
     * @since  1.1.0
     * @return int read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val int: Int
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
            return expandableByteBuffer!!.buffer.int
        }

    /**
     * Get float from the buffer
     *
     * @since  1.1.0
     * @return float read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val float: Float
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Float.BYTES)
            return expandableByteBuffer!!.buffer.float
        }

    /**
     * Get double from the buffer
     *
     * @since  1.1.0
     * @return double read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val double: Double
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Double.BYTES)
            return expandableByteBuffer!!.buffer.double
        }

    /**
     * Get byte from the buffer
     *
     * @since  1.1.0
     * @return byte read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val byte: Byte
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES)
            return expandableByteBuffer!!.buffer.get()
        }

    /**
     * Get short from the buffer
     *
     * @since  1.1.0
     * @return short read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val short: Short
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Short.BYTES)
            return expandableByteBuffer!!.buffer.short
        }

    /**
     * Get boolean from the buffer
     *
     * @since  1.1.0
     * @return boolean read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val boolean: Boolean
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES)
            return expandableByteBuffer!!.buffer.get().toInt() == 1
        }

    /**
     * Get char from the buffer
     *
     * @since  1.1.0
     * @return char read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val char: Char
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(Character.BYTES)
            return expandableByteBuffer!!.buffer.char
        }

    /**
     * Get a generic value.  Note: This must have been wrapped to ensure the type was added to the buffer so we know what we are
     * getting.  This will read the value detect if it is null, a reference, and gather the type to read it into.
     *
     * @since  1.1.0
     * @return Object read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val value: Any?
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES)

            when (val bufferObjectType = BufferObjectType.enumValues[expandableByteBuffer!!.buffer.get().toInt()]) {
                BufferObjectType.NULL -> return null
                BufferObjectType.REFERENCE -> return referenceOf(short.toInt())
                BufferObjectType.ENTITY -> return entity
                BufferObjectType.ENUM -> return enum
                BufferObjectType.BYTE, BufferObjectType.MUTABLE_BYTE -> return byte
                BufferObjectType.INT, BufferObjectType.MUTABLE_INT -> return int
                BufferObjectType.LONG, BufferObjectType.MUTABLE_LONG -> return long
                BufferObjectType.SHORT, BufferObjectType.MUTABLE_SHORT -> return short
                BufferObjectType.FLOAT, BufferObjectType.MUTABLE_FLOAT -> return float
                BufferObjectType.DOUBLE, BufferObjectType.MUTABLE_DOUBLE -> return double
                BufferObjectType.BOOLEAN, BufferObjectType.MUTABLE_BOOLEAN -> return boolean
                BufferObjectType.CHAR, BufferObjectType.MUTABLE_CHAR -> return char
                BufferObjectType.BYTE_ARRAY, BufferObjectType.INT_ARRAY, BufferObjectType.LONG_ARRAY, BufferObjectType.SHORT_ARRAY, BufferObjectType.FLOAT_ARRAY, BufferObjectType.DOUBLE_ARRAY, BufferObjectType.BOOLEAN_ARRAY, BufferObjectType.CHAR_ARRAY, BufferObjectType.OBJECT_ARRAY, BufferObjectType.OTHER_ARRAY -> return getArray(bufferObjectType)
                BufferObjectType.BUFFERED -> return buffered
                BufferObjectType.DATE -> return date
                BufferObjectType.STRING -> return string
                BufferObjectType.CLASS -> return objectClass
                BufferObjectType.COLLECTION -> return collection
                BufferObjectType.MAP -> return map
                BufferObjectType.OTHER -> return other
            }
        }

    /**
     * Get value from the buffer that is not a pre defined value.  This will iterate through the fields and
     * de-serialize them individually based on their type.
     *
     * @return The Object read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val other: Any
        @Throws(BufferingException::class)
        get() {
            val objectType = value as Class<*>
            val instance: Any
            try {
                instance = objectType.instance(this.context?.contextId ?: "")
                addReference(instance)

                instance.getFields(this.context?.contextId ?: "").forEach {
                    when (it.type) {
                        ClassMetadata.LONG_PRIMITIVE_TYPE -> instance.setLong(it, long)
                        ClassMetadata.INT_PRIMITIVE_TYPE -> instance.setInt(it, int)
                        ClassMetadata.DOUBLE_PRIMITIVE_TYPE -> instance.setDouble(it, double)
                        ClassMetadata.FLOAT_PRIMITIVE_TYPE -> instance.setFloat(it, float)
                        ClassMetadata.BYTE_PRIMITIVE_TYPE -> instance.setByte(it, byte)
                        ClassMetadata.CHAR_PRIMITIVE_TYPE -> instance.setChar(it, char)
                        ClassMetadata.SHORT_PRIMITIVE_TYPE -> instance.setShort(it, short)
                        ClassMetadata.BOOLEAN_PRIMITIVE_TYPE -> instance.setBoolean(it, boolean)
                        else -> instance.setObject(it, value)
                    }
                }

            } catch (e: Exception) {
                when (e) {
                    is BufferUnderflowException -> throw com.onyx.exception.BufferUnderflowException(com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW, objectType)
                    is BufferingException -> throw e
                    else -> throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE, objectType)
                }
            }

            return instance
        }

    /**
     * Get value class type from the buffer
     *
     * @since  1.1.0
     * @return class type read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @Suppress("MemberVisibilityCanPrivate")
    val objectClass: Class<*>
        @Throws(BufferingException::class)
        get() {
            val stringSize = expandableByteBuffer!!.buffer.int
            val stringBytes = ByteArray(stringSize)
            expandableByteBuffer!!.buffer.get(stringBytes)
            val className = String(stringBytes)
            return try {
                val returnValue = metadata.classForName(className, context)
                addReference(returnValue)
                returnValue
            } catch (e: ClassNotFoundException) {
                throw BufferingException(BufferingException.UNKNOWN_CLASS + className, null, e)
            }
        }

    /**
     * Get an entity from the buffer
     *
     * @since 2.1.3 Optimize entity store serialization
     */
    open val entity:IManagedEntity
        get() {
            val serializerId = expandableByteBuffer!!.buffer.int
            expandableByteBuffer!!.buffer.position(expandableByteBuffer!!.buffer.position() - Integer.BYTES)
            val systemEntity = context!!.getSystemEntityById(serializerId)
            val entity:ManagedEntity = systemEntity!!.type(context?.contextId ?: "").instance(contextId = context?.contextId ?: "") as ManagedEntity
            entity.read(this, context)
            return entity
        }

    /**
     * Get string from the buffer
     *
     * @since  1.1.0
     * @return string read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val string: String
        @Throws(BufferingException::class)
        get() {
            val stringSize = expandableByteBuffer!!.buffer.int
            val stringBytes = ByteArray(stringSize)
            expandableByteBuffer!!.buffer.get(stringBytes)
            return String(stringBytes)
        }

    /**
     * Get Date from the buffer.  This uses the epoch timestamp
     *
     * @since  1.1.0
     * @return Date read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val date: Date
        @Throws(BufferingException::class)
        get() {
            val epoch = expandableByteBuffer!!.buffer.long
            return Date(epoch)
        }

    /**
     * Get Collection from the buffer.  If there is an exception during instantiation, this will fail and
     * cause the entire de-serialization to fail.  You must add a public Collection class to the buffer rather
     * than a static class.  Also, it must exist on both the reader and writer jvm.
     *
     * @since  1.1.0
     * @return Collection read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val collection: Collection<*>
        @Throws(BufferingException::class)
        get() {

            val collectionClass = value as Class<*>?
            val size = expandableByteBuffer!!.buffer.int

            val collection = try {
                if(collectionClass == ArrayList::class.java || collectionClass == SortedList::class.java || Modifier.isPrivate(collectionClass!!.modifiers))
                    ArrayList()
                else
                    collectionClass.instance<MutableCollection<Any?>>(context?.contextId ?: "")
            } catch (e: Exception) {
                ArrayList()
            }

            for (i in 0 until size)
                collection.add(value)

            return collection
        }

    /**
     * Get Map from the buffer.  If there is an exception during instantiation, this will fail and
     * cause the entire de-serialization to fail.  You must add a public Map class to the buffer rather
     * than a static class.  Also, it must exist on both the reader and writer jvm.
     *
     * @since  1.1.0
     * @return Map read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val map: Map<*, *>
        @Throws(BufferingException::class)
        get() {
            val mapClass = value as Class<*>
            val map:MutableMap<Any,Any?> = try {
                mapClass.instance(context?.contextId ?: "")
            } catch (e: InstantiationException) {
                throw BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass)
            } catch (e: IllegalAccessException) {
                throw BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass)
            }

            val mapSize = expandableByteBuffer!!.buffer.int

            for (i in 0 until mapSize)
                map[value!!] = value

            return map
        }

    /**
     * Get an enum from the buffer
     *
     * @since 1.1.0
     * @return The enum from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val enum: Enum<*>
        @Throws(BufferingException::class)
        get() {
            val enumClass = value as Class<*>?
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES)

            return enumClass!!.enumConstants[byte.toInt()] as Enum<*>
        }

    /**
     * Get Buffer Streamable value that implements BufferStreamable interface.
     * First pulls the type of class, instantiates and invokes the read method.
     *
     * @since 1.1.0
     * @return An instantiated BufferStreamable value
     * @throws BufferingException Generic Buffer Exception
     */
    @Suppress("MemberVisibilityCanPrivate")
    val buffered: BufferStreamable
        @Throws(BufferingException::class)
        get() {
            val classToInstantiate = value as Class<*>?
            val streamable = classToInstantiate!!.instance<BufferStreamable>(context?.contextId ?: "")
            if (context == null)
                streamable.read(this)
            else
                streamable.read(this, context)
            return streamable
        }


    /**
     * Get a generic value.  Note: This must have been wrapped to ensure the type was added to the buffer so we know what we are
     * getting.  This will read the value detect if it is null, a reference, and gather the type to read it into.
     *
     * @since  1.1.0
     * @return Object read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun getObject(context: SchemaContext?): Any? {
        this.context = context
        return value
    }

    /**
     * Get Array of primitives or objects
     *
     * @since 1.1.0
     * @param type The serializer type that specifies which type of array to de-serialize
     * @return An Array
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun getArray(type: BufferObjectType): Any {
        when {
            type === BufferObjectType.LONG_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = LongArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(java.lang.Long.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.long
                return arr
            }
            type === BufferObjectType.INT_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = IntArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.int
                return arr
            }
            type === BufferObjectType.FLOAT_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = FloatArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(java.lang.Float.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.float
                return arr
            }
            type === BufferObjectType.BYTE_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = ByteArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.get()
                return arr
            }
            type === BufferObjectType.CHAR_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = CharArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(Character.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.char
                return arr
            }
            type === BufferObjectType.SHORT_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = ShortArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(java.lang.Short.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.short
                return arr
            }
            type === BufferObjectType.BOOLEAN_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = BooleanArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.get().toInt() == 1
                return arr
            }
            type === BufferObjectType.DOUBLE_ARRAY -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = DoubleArray(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(java.lang.Double.BYTES * arr.size)
                for (i in arr.indices)
                    arr[i] = expandableByteBuffer!!.buffer.double
                return arr
            }
            type === BufferObjectType.OTHER_ARRAY -> {
                val arr = Array.newInstance(objectClass, int)
                for (i in 0 until Array.getLength(arr)) {
                    val value = value
                    Array.set(arr, i, value)
                }
                return arr
            }
            else -> {
                expandableByteBuffer!!.ensureRequiredSize(Integer.BYTES)
                val arr = arrayOfNulls<Any>(expandableByteBuffer!!.buffer.int)
                expandableByteBuffer!!.ensureRequiredSize(3 * arr.size)
                for (i in arr.indices)
                    arr[i] = value
                return arr
            }
        }
    }

    // endregion

    // region Write Buffer

    /**
     * Put an enum key to the buffer
     *
     * @since 1.1.0
     * @param enumVal enum to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    private fun putEnum(enumVal: Enum<*>) {
        putObject(enumVal.javaClass)
        putByte(enumVal.ordinal.toByte())
    }

    /**
     * Put an array of elements.  The elements can be an array of primitives or mutable objects
     *
     * @since 1.1.0
     * @param array Array to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putArray(array: Any?) = when {
        array!!.javaClass == ClassMetadata.LONG_ARRAY-> {
            val arr = array as LongArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(java.lang.Long.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.putLong(anArr)
        }
        array.javaClass == ClassMetadata.INT_ARRAY -> {
            val arr = array as IntArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(Integer.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.putInt(anArr)
        }
        array.javaClass == ClassMetadata.FLOAT_ARRAY -> {
            val arr = array as FloatArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(java.lang.Float.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.putFloat(anArr)
        }
        array.javaClass == ClassMetadata.BYTE_ARRAY -> {
            val arr = array as ByteArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(java.lang.Byte.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.put(anArr)
        }
        array.javaClass == ClassMetadata.CHAR_ARRAY -> {
            val arr = array as CharArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(Character.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.putChar(anArr)
        }
        array.javaClass == ClassMetadata.SHORT_ARRAY -> {
            val arr = array as ShortArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(java.lang.Short.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.putShort(anArr)
        }
        array.javaClass == ClassMetadata.BOOLEAN_ARRAY -> {
            val arr = array as BooleanArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(java.lang.Byte.BYTES * arr.size)
            for (anArr in arr) expandableByteBuffer!!.buffer.put((if (anArr) 1 else 0).toByte())
        }
        array.javaClass == ClassMetadata.DOUBLE_ARRAY -> {
            val arr = array as DoubleArray
            putInt(arr.size)
            expandableByteBuffer!!.ensureSize(java.lang.Double.BYTES * arr.size)
            for (anArr in arr) putDouble(anArr)
        }
        array.javaClass == kotlin.Array<Any?>::class.java -> {
            @Suppress("UNCHECKED_CAST")
            val arr = array as kotlin.Array<Any?>
            putInt(arr.size)
            for (anArr in arr) putObject(anArr)
        }
        else -> {
            putObjectClass(array.javaClass.componentType)
            @Suppress("UNCHECKED_CAST")
            val arr = array as kotlin.Array<Any?>
            putInt(arr.size)
            for (anArr in arr) putObject(anArr)
        }
    }

    /**
     * Put an String to the buffer
     *
     * @since 1.1.0
     * @param value String to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putString(value: String) {
        val stringBytes = value.toByteArray()

        putInt(stringBytes.size)
        expandableByteBuffer!!.ensureSize(stringBytes.size)
        expandableByteBuffer!!.buffer.put(stringBytes)
    }

    /**
     * Put an Date to the buffer.  This stores as an epoch timestamp
     *
     * @since 1.1.0
     * @param value Date to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Suppress("MemberVisibilityCanPrivate")
    @Throws(BufferingException::class)
    fun putDate(value: Date) = putLong(value.time)

    /**
     * Put a Class to the buffer
     *
     * @since 1.1.0
     * @param type Class to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    private fun putObjectClass(type: Class<*>) {

        addReference(type)

        val className = type.name
        val stringBytes = className.toByteArray()

        putInt(stringBytes.size)

        expandableByteBuffer!!.ensureSize(stringBytes.size)
        expandableByteBuffer!!.buffer.put(stringBytes)
    }

    /**
     * Put a Collection to a buffer.  If the Collection class is un-accessible it will default to an ArrayCollection
     *
     * @since 1.1.0
     * @param collection Collection to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putCollection(collection: Collection<*>) {

        try {
            val clazz = metadata.classForName(collection.javaClass.name, context)
            putObject(clazz)
        } catch (e: ClassNotFoundException) {
            putObject(ArrayList::class.java)
        }

        putInt(collection.size)

        collection.forEach { putObject(it) }
    }

    /**
     * Put a Map to the buffer.  If the structure instance class is un-accessible it will chose to use a HashMap
     *
     * @since 1.1.0
     * @param map Map to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Suppress("MemberVisibilityCanPrivate")
    @Throws(BufferingException::class)
    fun putMap(map: Map<*, *>) {

        try {
            val clazz = metadata.classForName(map.javaClass.name, context)
            putObject(clazz)
        } catch (e: ClassNotFoundException) {
            putObject(HashMap::class.java)
        }

        putInt(map.size)

        map.forEach {
            putObject(it.key)
            putObject(it.value)
        }
    }

    /**
     * Put an entity without putting the class information
     *
     * @since 2.1.3 Optimize serialization
     */
    protected open fun putEntity(entity: ManagedEntity, context: SchemaContext?) {
        entity.write(this, context)
    }

    /**
     * Put an value that implements BufferStreamable to the buffer.
     *
     * @since 1.1.0
     * @param bufferStreamable BufferStreamable to write to the buffer
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    private fun putBuffered(bufferStreamable: BufferStreamable) {
        putObject(bufferStreamable.javaClass)
        if (context == null)
            bufferStreamable.write(this)
        else
            bufferStreamable.write(this, context)
    }

    /**
     * For all mutable objects that are not pre-defined, use this method to put that to the buffer
     *
     * This will iterate through all the fields and put each attribute to the buffer.  If your class
     * requires custom serialization I recommend using BufferStreamable interface.  Also, to ensure
     * the reference is working as it should please implement the hashCode and equals methods to make
     * sure we can identify the value and is unique.
     *
     * @since 1.1.0
     * @param `value` Generic mutable value to write to the buffer
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putOther(value: Any?) {

        putObject(value?.javaClass)
        if(value != null)
            addReference(value)

        // Iterate through the fields and put them on the expandableByteBuffer
        value?.getFields(context?.contextId ?: "")?.forEach {
            try {
                when (it.type) {
                    ClassMetadata.INT_PRIMITIVE_TYPE -> putInt(value.getInt(it))
                    ClassMetadata.LONG_PRIMITIVE_TYPE -> putLong(value.getLong(it))
                    ClassMetadata.BYTE_PRIMITIVE_TYPE -> putByte(value.getByte(it))
                    ClassMetadata.FLOAT_PRIMITIVE_TYPE -> putFloat(value.getFloat(it))
                    ClassMetadata.DOUBLE_PRIMITIVE_TYPE -> putDouble(value.getDouble(it))
                    ClassMetadata.BOOLEAN_PRIMITIVE_TYPE -> putBoolean(value.getBoolean(it))
                    ClassMetadata.SHORT_PRIMITIVE_TYPE -> putShort(value.getShort(it))
                    ClassMetadata.CHAR_PRIMITIVE_TYPE -> putChar(value.getChar(it))
                    else -> putObject(value.getObject(it))
                }
            } catch (e: IllegalAccessException) {
                throw BufferingException(BufferingException.ILLEGAL_ACCESS_EXCEPTION + it.name)
            }

        }
    }

    /**
     * Put byte to the buffer
     *
     * @since 1.1.0
     * @param value byte to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putByte(value: Byte) {
        expandableByteBuffer!!.ensureSize(java.lang.Byte.BYTES)
        expandableByteBuffer!!.buffer.put(value)
    }

    /**
     * Put an int to the buffer
     *
     * @since 1.1.0
     * @param value int to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putInt(value: Int) {
        expandableByteBuffer!!.ensureSize(Integer.BYTES)
        expandableByteBuffer!!.buffer.putInt(value)
    }

    /**
     * Put long to the buffer
     *
     * @since 1.1.0
     * @param value long to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putLong(value: Long) {
        expandableByteBuffer!!.ensureSize(java.lang.Long.BYTES)
        expandableByteBuffer!!.buffer.putLong(value)
    }

    /**
     * Put short to the buffer
     *
     * @since 1.1.0
     * @param value short to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putShort(value: Short) {
        expandableByteBuffer!!.ensureSize(java.lang.Short.BYTES)
        expandableByteBuffer!!.buffer.putShort(value)
    }

    /**
     * Put float to the buffer
     *
     * @since 1.1.0
     * @param value float to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    private fun putFloat(value: Float) {
        expandableByteBuffer!!.ensureSize(java.lang.Float.BYTES)
        expandableByteBuffer!!.buffer.putFloat(value)
    }

    /**
     * Put double to the buffer
     *
     * @since 1.1.0
     * @param value double to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putDouble(value: Double) {
        expandableByteBuffer!!.ensureSize(java.lang.Double.BYTES)
        expandableByteBuffer!!.buffer.putDouble(value)
    }

    /**
     * Put boolean to the buffer
     *
     * @since 1.1.0
     * @param value boolean to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putBoolean(value: Boolean) {
        expandableByteBuffer!!.ensureSize(java.lang.Byte.BYTES)
        expandableByteBuffer!!.buffer.put(if (value) 1.toByte() else 0.toByte())
    }

    /**
     * Put char to the buffer
     *
     * @since 1.1.0
     * @param value byte to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    private fun putChar(value: Char) {
        expandableByteBuffer!!.ensureSize(Character.BYTES)
        expandableByteBuffer!!.buffer.putChar(value)
    }

    /**
     * Put object with a context
     *
     * @param value Object to put into buffer
     * @param context Schema context
     */
    fun putObject(value:Any?, context: SchemaContext?):Int {
        this.context = context
        return putObject(value)
    }

    /**
     * Put object into the buffer stream
     *
     * @param value Object to put into buffer
     */
    @Throws(BufferingException::class)
    fun putObject(value: Any?): Int {
        val position = this.expandableByteBuffer!!.buffer.position()

        var bufferObjectType = BufferObjectType.getTypeCodeForClass(value, context)
        val referenceNumber = referenceIndex(value).toShort()
        if (referenceNumber > -1) bufferObjectType = BufferObjectType.REFERENCE

        try {

            // Put the serializer type
            putByte(bufferObjectType.ordinal.toByte())

            when (bufferObjectType) {
                BufferObjectType.NULL -> return this.expandableByteBuffer!!.buffer.position() - position
                BufferObjectType.REFERENCE -> putShort(referenceNumber)
                BufferObjectType.ENTITY -> putEntity(value as ManagedEntity, context)
                BufferObjectType.ENUM -> putEnum(value as Enum<*>)
                BufferObjectType.BYTE, BufferObjectType.MUTABLE_BYTE -> putByte(value as Byte)
                BufferObjectType.INT, BufferObjectType.MUTABLE_INT -> putInt(value as Int)
                BufferObjectType.LONG, BufferObjectType.MUTABLE_LONG -> putLong(value as Long)
                BufferObjectType.SHORT, BufferObjectType.MUTABLE_SHORT -> putShort(value as Short)
                BufferObjectType.FLOAT, BufferObjectType.MUTABLE_FLOAT -> putFloat(value as Float)
                BufferObjectType.DOUBLE, BufferObjectType.MUTABLE_DOUBLE -> putDouble(value as Double)
                BufferObjectType.BOOLEAN, BufferObjectType.MUTABLE_BOOLEAN -> putBoolean(value as Boolean)
                BufferObjectType.CHAR, BufferObjectType.MUTABLE_CHAR -> putChar(value as Char)
                BufferObjectType.BYTE_ARRAY, BufferObjectType.INT_ARRAY, BufferObjectType.LONG_ARRAY, BufferObjectType.SHORT_ARRAY, BufferObjectType.FLOAT_ARRAY, BufferObjectType.DOUBLE_ARRAY, BufferObjectType.BOOLEAN_ARRAY, BufferObjectType.CHAR_ARRAY, BufferObjectType.OBJECT_ARRAY, BufferObjectType.OTHER_ARRAY -> putArray(value)
                BufferObjectType.BUFFERED -> putBuffered(value as BufferStreamable)
                BufferObjectType.DATE -> putDate(value as Date)
                BufferObjectType.STRING -> putString(value as String)
                BufferObjectType.CLASS -> putObjectClass(value as Class<*>)
                BufferObjectType.COLLECTION -> putCollection(value as Collection<*>)
                BufferObjectType.MAP -> putMap(value as Map<*, *>)
                BufferObjectType.OTHER -> putOther(value)
            }
        } catch (e: Exception) {
            when (e) {
                is BufferUnderflowException -> throw com.onyx.exception.BufferUnderflowException(com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW, value!!.javaClass)
                is BufferingException -> throw e
                is OnyxException -> throw e
                else -> throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE, value?.javaClass, e)
            }
        }

        return this.expandableByteBuffer!!.buffer.position() - position
    }


    // endregion

    /**
     * Converts the buffer to a key key structure.  Note this is intended to use only with ManagedEntities
     *
     * @param context Schema context
     * @return Map representation of the value
     */
    open fun toMap(context: SchemaContext): Map<String, Any?> {
        val results = HashMap<String, Any?>()

        val type = byte  // Read the buffer value metadata
        if(type != BufferObjectType.ENTITY.ordinal.toByte())
            value // Read the entity type
        val systemEntity = context.getSystemEntityById(int)!!

        for ((name) in systemEntity.attributes) results[name] = value

        return results
    }

    companion object {

        /**
         * Convert an value to the byte buffer representation
         *
         * @param `any` Object to convert to a byte buffer
         * @param context Schema Context for managed entities
         * @return The ByteBuffer the value was serialized into
         * @since 1.1.0
         * @throws BufferingException Generic serialization exception when buffering
         */
        @Throws(BufferingException::class)
        @JvmOverloads
        @JvmStatic
        fun toBuffer(any: Any, context: SchemaContext? = null): ByteBuffer {

            val bufferStream = BufferStream(context)
            bufferStream.expandableByteBuffer!!.buffer.position(Integer.BYTES)
            bufferStream.putObject(any)
            bufferStream.expandableByteBuffer!!.buffer.flip()
            bufferStream.expandableByteBuffer!!.buffer.putInt(bufferStream.expandableByteBuffer!!.buffer.limit())
            bufferStream.expandableByteBuffer!!.buffer.rewind()

            return bufferStream.byteBuffer
        }

        /**
         * Convert a buffer to an value by de-serializing the bytes in the buffer
         * @param buffer Buffer to read from
         * @param context Schema context for managed entities
         * @return The value read from the buffer
         * @since 1.1.0
         * @throws BufferingException Generic de-serialization exception occurred when trying to generate
         */
        @Throws(BufferingException::class)
        @JvmOverloads
        @JvmStatic
        fun fromBuffer(buffer: ByteBuffer, context: SchemaContext? = null): Any? {

            val bufferStream = BufferStream(context)
            BufferPool.recycle(bufferStream.byteBuffer)

            val bufferStartingPosition = buffer.position()
            val maxBufferSize = buffer.int

            bufferStream.expandableByteBuffer = ExpandableByteBuffer(buffer, bufferStartingPosition, maxBufferSize)
            bufferStream.isComingFromBuffer = true

            val returnValue: Any? = try {
                bufferStream.value
            } catch (e: BufferingException) {
                buffer.position(maxBufferSize + bufferStartingPosition)
                throw e
            } catch (e: Exception) {
                buffer.position(maxBufferSize + bufferStartingPosition)
                if (e is BufferUnderflowException)
                    throw com.onyx.exception.BufferUnderflowException(com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW)
                else
                    throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE, null, e)
            }

            if (buffer.position() - bufferStartingPosition != maxBufferSize) {
                // Roll the expandableByteBuffer forward so that the next process does not get hung up at the previous position.
                buffer.position(maxBufferSize + bufferStartingPosition)

                // Serialization did not go right, that means we have a serious problem and we do not want it to lead to a corruption
                // therefore we are going to throw an exception
                throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE)
            }

            return returnValue
        }
    }
}
