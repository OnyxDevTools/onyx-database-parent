package com.onyx.buffer

import com.onyx.diskmap.data.bigInt
import com.onyx.diskmap.data.putBigInt
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
import kotlin.Pair
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Created by Tim Osborn on 7/2/1 6.
 *
 *
 * The expandableByteBuffer expandableByteBuffer is an value serialization expandableByteBuffer.   It is inspired by the InputStream and and OutputStream
 * and how it interacts with the Externalized interface.  An exception to that is that the underlying io is to
 * a ByteBuffer.
 */
open class BufferStream(buffer: ByteBuffer) {

    // region Properties

    protected var context: SchemaContext? = null

    private var cachedMetadata: ClassMetadata? = null
    val metadata: ClassMetadata
        get() = cachedMetadata ?: metadata(contextId = context?.contextId ?: "").also {
            cachedMetadata = it
        }

    // Number of references to retain the index number of the said reference
    private var referenceCount = 0

    // Wrapper for a ByteBuffer to retain reference
    var expandableByteBuffer: ExpandableByteBuffer? = ExpandableByteBuffer(buffer)

    // Indicates whether we are pulling from the expandableByteBuffer or putting into the expandableByteBuffer.
    private var isComingFromBuffer = false

    // True object references use identity, avoiding user equals/hashCode work and recursion.
    private var references: IdentityHashMap<Any, Int>? = null

    // References by index number ordered by first used
    private var referencesByIndex: ArrayList<Any?>? = null

    // Version 2 uses compact structural values while legacy streams remain readable.
    private var compactFormat = false

    // Strings are immutable, so value-based interning is safe and dramatically reduces repeated values.
    private var stringWriteReferences: HashMap<String, Int>? = null
    private var stringReadReferences: ArrayList<String>? = null

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
    private val referenceLimit: Int
        get() = if (compactFormat) MAX_COMPACT_REFERENCES else Short.MAX_VALUE.toInt()

    private fun addReference(reference: Any) {
        if (isComingFromBuffer) {
            val referenceIndex = reserveReference()
            if (referenceIndex > 0) {
                referencesByIndex!![referenceIndex - 1] = reference
            }
            return
        }

        if (referenceCount >= referenceLimit) return
        val referenceMap = references ?: IdentityHashMap<Any, Int>().also { references = it }
        if (!referenceMap.containsKey(reference)) {
            referenceMap[reference] = ++referenceCount
        }
    }

    /**
     * Reserve the next reader-side reference slot before reading nested values.
     * The writer registers pairs before their elements, so the reader must do the same.
     */
    private fun reserveReference(): Int {
        if (referenceCount >= referenceLimit) return -1

        if (referencesByIndex == null) referencesByIndex = ArrayList()
        referencesByIndex!!.add(null)
        return ++referenceCount
    }

    /**
     * The public ByteBuffer constructor supports both reading and writing. Switch to the
     * reader reference representation when the first typed value is read.
     */
    private fun beginReading() {
        if (isComingFromBuffer)
            return

        clearReferences()
        isComingFromBuffer = true
    }

    /**
     * Get the reference index of an value
     * @param reference Reference of an value
     * @return if it exists it will return the index number otherwise -1
     */
    private fun referenceIndex(reference: Any?): Int {
        if (reference == null)
            return -1

        return references?.get(reference) ?: -1
    }

    /**
     * Reference of the reference index
     * @param index Index to seek to
     * @return The actual value referenced
     */
    private fun referenceOf(index: Int): Any = referencesByIndex?.getOrNull(index - 1)!!

    //endregion

    // region Compact Encoding

    private fun putUnsignedInt(value: Int) {
        expandableByteBuffer!!.ensureSize(MAX_VAR_INT_BYTES)
        CompactBinary.putUnsignedInt(expandableByteBuffer!!.buffer, value)
    }

    private fun readUnsignedInt(): Int = CompactBinary.getUnsignedInt(expandableByteBuffer!!.buffer)

    private fun putDynamicInt(value: Int) {
        if (compactFormat) {
            expandableByteBuffer!!.ensureSize(MAX_VAR_INT_BYTES)
            CompactBinary.putSignedInt(expandableByteBuffer!!.buffer, value)
        } else {
            putInt(value)
        }
    }

    private fun readDynamicInt(): Int =
        if (compactFormat) CompactBinary.getSignedInt(expandableByteBuffer!!.buffer) else int

    private fun putDynamicLong(value: Long) {
        if (compactFormat) {
            expandableByteBuffer!!.ensureSize(MAX_VAR_LONG_BYTES)
            CompactBinary.putSignedLong(expandableByteBuffer!!.buffer, value)
        } else {
            putLong(value)
        }
    }

    private fun readDynamicLong(): Long =
        if (compactFormat) CompactBinary.getSignedLong(expandableByteBuffer!!.buffer) else long

    private fun putDynamicShort(value: Short) {
        if (compactFormat) putDynamicInt(value.toInt()) else putShort(value)
    }

    private fun readDynamicShort(): Short =
        if (compactFormat) readDynamicInt().toShort() else short

    private fun putDynamicChar(value: Char) {
        if (compactFormat) {
            putUnsignedInt(value.code)
        } else {
            putChar(value)
        }
    }

    private fun readDynamicChar(): Char =
        if (compactFormat) readUnsignedInt().toChar() else char

    private fun putSize(value: Int) {
        require(value >= 0) { "Serialized size must not be negative: $value" }
        if (compactFormat) putUnsignedInt(value) else putInt(value)
    }

    private fun readSize(): Int {
        val value = if (compactFormat) readUnsignedInt() else int
        require(value >= 0) { "Serialized size must not be negative: $value" }
        return value
    }

    private fun putReferenceIndex(value: Int) {
        if (compactFormat) putUnsignedInt(value) else putShort(value.toShort())
    }

    private fun readReferenceIndex(): Int =
        if (compactFormat) readUnsignedInt() else short.toInt()

    private fun utf8Length(value: String): Int {
        var byteCount = 0L
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.code < 0x80 -> byteCount += 1
                char.code < 0x800 -> byteCount += 2
                Character.isHighSurrogate(char) &&
                        index + 1 < value.length &&
                        Character.isLowSurrogate(value[index + 1]) -> {
                    byteCount += 4
                    index++
                }
                Character.isSurrogate(char) -> byteCount += 1 // UTF-8 replacement byte '?'
                else -> byteCount += 3
            }
            if (byteCount > MAX_COMPACT_STRING_BYTES) {
                throw IllegalArgumentException("String is too large to serialize")
            }
            index++
        }
        return byteCount.toInt()
    }

    private fun putUtf8Bytes(value: String, byteCount: Int) {
        expandableByteBuffer!!.ensureSize(byteCount)
        val buffer = expandableByteBuffer!!.buffer
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val code = char.code
            when {
                code < 0x80 -> buffer.put(code.toByte())
                code < 0x800 -> {
                    buffer.put((0xc0 or (code ushr 6)).toByte())
                    buffer.put((0x80 or (code and 0x3f)).toByte())
                }
                Character.isHighSurrogate(char) &&
                        index + 1 < value.length &&
                        Character.isLowSurrogate(value[index + 1]) -> {
                    val codePoint = Character.toCodePoint(char, value[++index])
                    buffer.put((0xf0 or (codePoint ushr 18)).toByte())
                    buffer.put((0x80 or ((codePoint ushr 12) and 0x3f)).toByte())
                    buffer.put((0x80 or ((codePoint ushr 6) and 0x3f)).toByte())
                    buffer.put((0x80 or (codePoint and 0x3f)).toByte())
                }
                Character.isSurrogate(char) -> buffer.put('?'.code.toByte())
                else -> {
                    buffer.put((0xe0 or (code ushr 12)).toByte())
                    buffer.put((0x80 or ((code ushr 6) and 0x3f)).toByte())
                    buffer.put((0x80 or (code and 0x3f)).toByte())
                }
            }
            index++
        }
    }

    private fun putRawUtf8(value: String) {
        val byteCount = utf8Length(value)
        putUnsignedInt(byteCount)
        putUtf8Bytes(value, byteCount)
    }

    private fun readRawUtf8(): String {
        val byteCount = readUnsignedInt()
        require(byteCount >= 0) { "String size must not be negative: $byteCount" }
        expandableByteBuffer!!.ensureRequiredSize(byteCount)
        val bytes = ByteArray(byteCount)
        expandableByteBuffer!!.buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun putCompactString(value: String) {
        val shouldIntern = value.length >= MIN_INTERNED_STRING_LENGTH
        val existingIndex = if (shouldIntern) stringWriteReferences?.get(value) else null
        if (existingIndex != null) {
            putUnsignedInt((existingIndex + 1) shl 1)
            return
        }

        val byteCount = utf8Length(value)
        require(byteCount <= MAX_COMPACT_STRING_BYTES)
        putUnsignedInt((byteCount shl 1) or 1)
        putUtf8Bytes(value, byteCount)

        if (shouldIntern) {
            if (stringWriteReferences == null) stringWriteReferences = HashMap()
            if (stringWriteReferences!!.size < MAX_INTERNED_STRINGS) {
                stringWriteReferences!![value] = stringWriteReferences!!.size
            }
        }
    }

    private fun readCompactString(): String {
        val token = readUnsignedInt()
        if (token and 1 == 0) {
            val index = (token ushr 1) - 1
            require(index >= 0) { "Invalid compact string reference" }
            return stringReadReferences?.getOrNull(index)
                ?: throw IllegalArgumentException("Unknown compact string reference: $index")
        }

        val byteCount = token ushr 1
        expandableByteBuffer!!.ensureRequiredSize(byteCount)
        val bytes = ByteArray(byteCount)
        expandableByteBuffer!!.buffer.get(bytes)
        val value = String(bytes, Charsets.UTF_8)

        if (value.length >= MIN_INTERNED_STRING_LENGTH) {
            if (stringReadReferences == null) stringReadReferences = ArrayList()
            if (stringReadReferences!!.size < MAX_INTERNED_STRINGS) {
                stringReadReferences!!.add(value)
            }
        }
        return value
    }

    // endregion

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
        isComingFromBuffer = false
        cachedMetadata = null
    }

    /**
     * Clear references so that it forces the re-serialization of objects rather than inserting reference
     * placeholders into the buffer.
     *
     * @since 2.0.0
     */
    private fun clearReferences() {
        if ((references?.size ?: 0) > MAX_RETAINED_REFERENCE_CAPACITY)
            references = null
        else
            references?.clear()

        if ((referencesByIndex?.size ?: 0) > MAX_RETAINED_REFERENCE_CAPACITY)
            referencesByIndex = null
        else
            referencesByIndex?.clear()

        if ((stringWriteReferences?.size ?: 0) > MAX_RETAINED_STRING_CAPACITY)
            stringWriteReferences = null
        else
            stringWriteReferences?.clear()

        if ((stringReadReferences?.size ?: 0) > MAX_RETAINED_STRING_CAPACITY)
            stringReadReferences = null
        else
            stringReadReferences?.clear()

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
     * Get Long from the buffer
     *
     * @since  1.1.0
     * @return long read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val bigInt: Long
        @Throws(BufferingException::class)
        get() {
            expandableByteBuffer!!.ensureRequiredSize(5)
            return expandableByteBuffer!!.buffer.bigInt
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
            beginReading()
            expandableByteBuffer!!.ensureRequiredSize(java.lang.Byte.BYTES)

            val typeOrdinal = expandableByteBuffer!!.buffer.get().toInt() and 0xff
            val bufferObjectType = BufferObjectType.enumValues.getOrNull(typeOrdinal)
                ?: throw IllegalArgumentException("Unknown serialized object type: $typeOrdinal")

            when (bufferObjectType) {
                BufferObjectType.NULL -> return null
                BufferObjectType.REFERENCE -> return referenceOf(readReferenceIndex())
                BufferObjectType.ENTITY -> return entity
                BufferObjectType.ENUM -> return enum
                BufferObjectType.BYTE, BufferObjectType.MUTABLE_BYTE -> return byte
                BufferObjectType.INT, BufferObjectType.MUTABLE_INT -> return readDynamicInt()
                BufferObjectType.LONG, BufferObjectType.MUTABLE_LONG -> return readDynamicLong()
                BufferObjectType.SHORT, BufferObjectType.MUTABLE_SHORT -> return readDynamicShort()
                BufferObjectType.FLOAT, BufferObjectType.MUTABLE_FLOAT -> return float
                BufferObjectType.DOUBLE, BufferObjectType.MUTABLE_DOUBLE -> return double
                BufferObjectType.BOOLEAN, BufferObjectType.MUTABLE_BOOLEAN -> return boolean
                BufferObjectType.CHAR, BufferObjectType.MUTABLE_CHAR -> return readDynamicChar()
                BufferObjectType.BYTE_ARRAY, BufferObjectType.INT_ARRAY, BufferObjectType.LONG_ARRAY, BufferObjectType.SHORT_ARRAY, BufferObjectType.FLOAT_ARRAY, BufferObjectType.DOUBLE_ARRAY, BufferObjectType.BOOLEAN_ARRAY, BufferObjectType.CHAR_ARRAY, BufferObjectType.OBJECT_ARRAY, BufferObjectType.OTHER_ARRAY -> return getArray(bufferObjectType)
                BufferObjectType.BUFFERED -> return buffered
                BufferObjectType.DATE -> return date
                BufferObjectType.STRING -> return string
                BufferObjectType.CLASS -> return objectClass
                BufferObjectType.PAIR -> return pair
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

                instance.getSerializationFields(this.context?.contextId ?: "").forEach { serializedField ->
                    val javaField = serializedField.field
                    when (serializedField.kind) {
                        SerializationFieldKind.LONG -> instance.setLong(javaField, readDynamicLong())
                        SerializationFieldKind.INT -> instance.setInt(javaField, readDynamicInt())
                        SerializationFieldKind.DOUBLE -> instance.setDouble(javaField, double)
                        SerializationFieldKind.FLOAT -> instance.setFloat(javaField, float)
                        SerializationFieldKind.BYTE -> instance.setByte(javaField, byte)
                        SerializationFieldKind.CHAR -> instance.setChar(javaField, readDynamicChar())
                        SerializationFieldKind.SHORT -> instance.setShort(javaField, readDynamicShort())
                        SerializationFieldKind.BOOLEAN -> instance.setBoolean(javaField, boolean)
                        else -> instance.setObject(javaField, value)
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
            beginReading()
            val className = if (compactFormat) {
                readRawUtf8()
            } else {
                val stringSize = expandableByteBuffer!!.buffer.int
                expandableByteBuffer!!.ensureRequiredSize(stringSize)
                val stringBytes = ByteArray(stringSize)
                expandableByteBuffer!!.buffer.get(stringBytes)
                String(stringBytes)
            }
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
        get() = if (compactFormat) {
            readCompactString()
        } else {
            val stringSize = expandableByteBuffer!!.buffer.int
            expandableByteBuffer!!.ensureRequiredSize(stringSize)
            val stringBytes = ByteArray(stringSize)
            expandableByteBuffer!!.buffer.get(stringBytes)
            String(stringBytes)
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
        get() = Date(readDynamicLong())

    /**
     * Get Pair from the buffer.
     *
     * @return Pair read from the buffer
     * @throws BufferingException Generic Buffer Exception
     */
    val pair: Pair<Any?, Any?>
        @Throws(BufferingException::class)
        get() {
            beginReading()
            val referenceIndex = reserveReference()
            val first = value
            val second = value
            val pair = Pair(first, second)
            if (referenceIndex > 0)
                referencesByIndex!![referenceIndex - 1] = pair
            return pair
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
            val collectionKind: Int
            val collectionClass: Class<*>?
            if (compactFormat) {
                collectionKind = byte.toInt() and 0xff
                collectionClass = if (collectionKind == COLLECTION_OTHER) value as Class<*> else null
            } else {
                collectionKind = COLLECTION_OTHER
                collectionClass = value as Class<*>?
            }

            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(size)
            val initialCapacity = size.coerceAtLeast(0)
            val collection: MutableCollection<Any?> = try {
                when (collectionKind) {
                    COLLECTION_ARRAY_LIST -> ArrayList(initialCapacity)
                    COLLECTION_HASH_SET -> HashSet(collectionCapacity(initialCapacity))
                    COLLECTION_LINKED_HASH_SET -> LinkedHashSet(collectionCapacity(initialCapacity))
                    else -> when {
                        collectionClass == ArrayList::class.java ||
                                collectionClass == SortedList::class.java ||
                                Modifier.isPrivate(collectionClass!!.modifiers) -> ArrayList(initialCapacity)
                        collectionClass == HashSet::class.java -> HashSet(collectionCapacity(initialCapacity))
                        collectionClass == LinkedHashSet::class.java -> LinkedHashSet(collectionCapacity(initialCapacity))
                        else -> collectionClass.instance(context?.contextId ?: "")
                    }
                }
            } catch (_: Exception) {
                ArrayList(initialCapacity)
            }

            repeat(size) { collection.add(value) }
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
            val mapKind: Int
            val mapClass: Class<*>?
            if (compactFormat) {
                mapKind = byte.toInt() and 0xff
                mapClass = if (mapKind == MAP_OTHER) value as Class<*> else null
            } else {
                mapKind = MAP_OTHER
                mapClass = value as Class<*>
            }

            val mapSize = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(2, mapSize))
            val initialCapacity = collectionCapacity(mapSize.coerceAtLeast(0))
            val map: MutableMap<Any?, Any?> = try {
                when (mapKind) {
                    MAP_HASH -> HashMap(initialCapacity)
                    MAP_LINKED_HASH -> LinkedHashMap(initialCapacity)
                    else -> when (mapClass) {
                        HashMap::class.java -> HashMap(initialCapacity)
                        LinkedHashMap::class.java -> LinkedHashMap(initialCapacity)
                        else -> mapClass!!.instance<MutableMap<Any?, Any?>>(context?.contextId ?: "")
                    }
                }
            } catch (_: InstantiationException) {
                throw BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass)
            } catch (_: IllegalAccessException) {
                throw BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass)
            }

            repeat(mapSize) { map[value] = value }
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

            val ordinal = if (compactFormat) readUnsignedInt() else byte.toInt() and 0xff
            return enumClass!!.enumConstants[ordinal] as Enum<*>
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
    fun getArray(type: BufferObjectType): Any = when (type) {
        BufferObjectType.LONG_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(java.lang.Long.BYTES, size))
            val arr = LongArray(size)
            for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.long
            arr
        }
        BufferObjectType.INT_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(Integer.BYTES, size))
            val arr = IntArray(size)
            for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.int
            arr
        }
        BufferObjectType.FLOAT_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(java.lang.Float.BYTES, size))
            val arr = FloatArray(size)
            for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.float
            arr
        }
        BufferObjectType.BYTE_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(size)
            val arr = ByteArray(size)
            expandableByteBuffer!!.buffer.get(arr)
            arr
        }
        BufferObjectType.CHAR_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(Character.BYTES, size))
            val arr = CharArray(size)
            for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.char
            arr
        }
        BufferObjectType.SHORT_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(java.lang.Short.BYTES, size))
            val arr = ShortArray(size)
            for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.short
            arr
        }
        BufferObjectType.BOOLEAN_ARRAY -> {
            val size = readSize()
            val payloadSize = if (compactFormat) ((size.toLong() + 7L) ushr 3).toInt() else size
            expandableByteBuffer!!.ensureRequiredSize(payloadSize)
            val arr = BooleanArray(size)
            if (compactFormat) {
                var index = 0
                while (index < arr.size) {
                    val packed = expandableByteBuffer!!.buffer.get().toInt() and 0xff
                    val valuesInByte = minOf(8, arr.size - index)
                    for (bit in 0 until valuesInByte) {
                        arr[index + bit] = packed and (1 shl bit) != 0
                    }
                    index += valuesInByte
                }
            } else {
                for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.get().toInt() == 1
            }
            arr
        }
        BufferObjectType.DOUBLE_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(Math.multiplyExact(java.lang.Double.BYTES, size))
            val arr = DoubleArray(size)
            for (index in arr.indices) arr[index] = expandableByteBuffer!!.buffer.double
            arr
        }
        BufferObjectType.OTHER_ARRAY -> {
            val componentType = objectClass
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(size)
            val arr = Array.newInstance(componentType, size)
            for (index in 0 until size) Array.set(arr, index, value)
            arr
        }
        BufferObjectType.OBJECT_ARRAY -> {
            val size = readSize()
            expandableByteBuffer!!.ensureRequiredSize(size)
            val arr = arrayOfNulls<Any>(size)
            for (index in arr.indices) arr[index] = value
            arr
        }
        else -> throw IllegalArgumentException("$type is not an array type")
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
        if (compactFormat) putUnsignedInt(enumVal.ordinal) else putByte(enumVal.ordinal.toByte())
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
    fun putArray(array: Any?) {
        when (array) {
            is LongArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(Math.multiplyExact(java.lang.Long.BYTES, array.size))
                for (item in array) expandableByteBuffer!!.buffer.putLong(item)
            }
            is IntArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(Math.multiplyExact(Integer.BYTES, array.size))
                for (item in array) expandableByteBuffer!!.buffer.putInt(item)
            }
            is FloatArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(Math.multiplyExact(java.lang.Float.BYTES, array.size))
                for (item in array) expandableByteBuffer!!.buffer.putFloat(item)
            }
            is ByteArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(array.size)
                expandableByteBuffer!!.buffer.put(array)
            }
            is CharArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(Math.multiplyExact(Character.BYTES, array.size))
                for (item in array) expandableByteBuffer!!.buffer.putChar(item)
            }
            is ShortArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(Math.multiplyExact(java.lang.Short.BYTES, array.size))
                for (item in array) expandableByteBuffer!!.buffer.putShort(item)
            }
            is BooleanArray -> {
                putSize(array.size)
                if (compactFormat) {
                    val packedByteCount = ((array.size.toLong() + 7L) ushr 3).toInt()
                    expandableByteBuffer!!.ensureSize(packedByteCount)
                    var index = 0
                    while (index < array.size) {
                        var packed = 0
                        val valuesInByte = minOf(8, array.size - index)
                        for (bit in 0 until valuesInByte) {
                            if (array[index + bit]) packed = packed or (1 shl bit)
                        }
                        expandableByteBuffer!!.buffer.put(packed.toByte())
                        index += valuesInByte
                    }
                } else {
                    expandableByteBuffer!!.ensureSize(array.size)
                    for (item in array) expandableByteBuffer!!.buffer.put(if (item) 1 else 0)
                }
            }
            is DoubleArray -> {
                putSize(array.size)
                expandableByteBuffer!!.ensureSize(Math.multiplyExact(java.lang.Double.BYTES, array.size))
                for (item in array) expandableByteBuffer!!.buffer.putDouble(item)
            }
            is kotlin.Array<*> -> {
                if (array.javaClass.componentType != Any::class.java) {
                    putObjectClass(array.javaClass.componentType)
                }
                putSize(array.size)
                for (item in array) putObject(item)
            }
            else -> throw IllegalArgumentException("Value is not an array: ${array?.javaClass?.name}")
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
        if (compactFormat) {
            putCompactString(value)
        } else {
            val stringBytes = value.toByteArray()
            putInt(stringBytes.size)
            expandableByteBuffer!!.ensureSize(stringBytes.size)
            expandableByteBuffer!!.buffer.put(stringBytes)
        }
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
    fun putDate(value: Date) = putDynamicLong(value.time)

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
        if (compactFormat) {
            putRawUtf8(className)
        } else {
            val stringBytes = className.toByteArray()
            putInt(stringBytes.size)
            expandableByteBuffer!!.ensureSize(stringBytes.size)
            expandableByteBuffer!!.buffer.put(stringBytes)
        }
    }

    /**
     * Put a Pair on the buffer stream.
     */
    @Throws(BufferingException::class)
    fun putPair(pair: Pair<*, *>) {
        addReference(pair)
        putObject(pair.first)
        putObject(pair.second)
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
        if (compactFormat) {
            val clazz = collection.javaClass
            val kind = when {
                clazz == ArrayList::class.java || clazz == SortedList::class.java || Modifier.isPrivate(clazz.modifiers) -> COLLECTION_ARRAY_LIST
                clazz == HashSet::class.java -> COLLECTION_HASH_SET
                clazz == LinkedHashSet::class.java -> COLLECTION_LINKED_HASH_SET
                else -> COLLECTION_OTHER
            }
            putByte(kind.toByte())
            if (kind == COLLECTION_OTHER) {
                try {
                    putObject(metadata.classForName(clazz.name, context))
                } catch (_: ClassNotFoundException) {
                    putObject(ArrayList::class.java)
                }
            }
        } else {
            try {
                putObject(metadata.classForName(collection.javaClass.name, context))
            } catch (_: ClassNotFoundException) {
                putObject(ArrayList::class.java)
            }
        }

        putSize(collection.size)
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
        if (compactFormat) {
            val clazz = map.javaClass
            val kind = when {
                clazz == HashMap::class.java || Modifier.isPrivate(clazz.modifiers) -> MAP_HASH
                clazz == LinkedHashMap::class.java -> MAP_LINKED_HASH
                else -> MAP_OTHER
            }
            putByte(kind.toByte())
            if (kind == MAP_OTHER) {
                try {
                    putObject(metadata.classForName(clazz.name, context))
                } catch (_: ClassNotFoundException) {
                    putObject(HashMap::class.java)
                }
            }
        } else {
            try {
                putObject(metadata.classForName(map.javaClass.name, context))
            } catch (_: ClassNotFoundException) {
                putObject(HashMap::class.java)
            }
        }

        putSize(map.size)
        map.forEach { (key, value) ->
            putObject(key)
            putObject(value)
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

        // Primitive/object field kinds are cached with the Field metadata once per class.
        value?.getSerializationFields(context?.contextId ?: "")?.forEach { serializedField ->
            val javaField = serializedField.field
            try {
                when (serializedField.kind) {
                    SerializationFieldKind.INT -> putDynamicInt(value.getInt(javaField))
                    SerializationFieldKind.LONG -> putDynamicLong(value.getLong(javaField))
                    SerializationFieldKind.BYTE -> putByte(value.getByte(javaField))
                    SerializationFieldKind.FLOAT -> putFloat(value.getFloat(javaField))
                    SerializationFieldKind.DOUBLE -> putDouble(value.getDouble(javaField))
                    SerializationFieldKind.BOOLEAN -> putBoolean(value.getBoolean(javaField))
                    SerializationFieldKind.SHORT -> putDynamicShort(value.getShort(javaField))
                    SerializationFieldKind.CHAR -> putDynamicChar(value.getChar(javaField))
                    else -> putObject(value.getObject(javaField))
                }
            } catch (_: IllegalAccessException) {
                throw BufferingException(BufferingException.ILLEGAL_ACCESS_EXCEPTION + javaField.name)
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
     * Put big int to the buffer
     *
     * @since 1.1.0
     * @param value long to write
     *
     * @throws BufferingException Generic Buffer Exception
     */
    @Throws(BufferingException::class)
    fun putBigInt(value: Long) {
        expandableByteBuffer!!.ensureSize(5)
        expandableByteBuffer!!.buffer.putBigInt(value)
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
        val referenceNumber = when (bufferObjectType) {
            BufferObjectType.CLASS, BufferObjectType.PAIR, BufferObjectType.OTHER -> referenceIndex(value)
            else -> -1
        }
        if (referenceNumber > -1) bufferObjectType = BufferObjectType.REFERENCE

        try {

            // Put the serializer type
            putByte(bufferObjectType.ordinal.toByte())

            when (bufferObjectType) {
                BufferObjectType.NULL -> return this.expandableByteBuffer!!.buffer.position() - position
                BufferObjectType.REFERENCE -> putReferenceIndex(referenceNumber)
                BufferObjectType.ENTITY -> putEntity(value as ManagedEntity, context)
                BufferObjectType.ENUM -> putEnum(value as Enum<*>)
                BufferObjectType.BYTE, BufferObjectType.MUTABLE_BYTE -> putByte(value as Byte)
                BufferObjectType.INT, BufferObjectType.MUTABLE_INT -> putDynamicInt(value as Int)
                BufferObjectType.LONG, BufferObjectType.MUTABLE_LONG -> putDynamicLong(value as Long)
                BufferObjectType.SHORT, BufferObjectType.MUTABLE_SHORT -> putDynamicShort(value as Short)
                BufferObjectType.FLOAT, BufferObjectType.MUTABLE_FLOAT -> putFloat(value as Float)
                BufferObjectType.DOUBLE, BufferObjectType.MUTABLE_DOUBLE -> putDouble(value as Double)
                BufferObjectType.BOOLEAN, BufferObjectType.MUTABLE_BOOLEAN -> putBoolean(value as Boolean)
                BufferObjectType.CHAR, BufferObjectType.MUTABLE_CHAR -> putDynamicChar(value as Char)
                BufferObjectType.BYTE_ARRAY, BufferObjectType.INT_ARRAY, BufferObjectType.LONG_ARRAY, BufferObjectType.SHORT_ARRAY, BufferObjectType.FLOAT_ARRAY, BufferObjectType.DOUBLE_ARRAY, BufferObjectType.BOOLEAN_ARRAY, BufferObjectType.CHAR_ARRAY, BufferObjectType.OBJECT_ARRAY, BufferObjectType.OTHER_ARRAY -> putArray(value)
                BufferObjectType.BUFFERED -> putBuffered(value as BufferStreamable)
                BufferObjectType.DATE -> putDate(value as Date)
                BufferObjectType.STRING -> putString(value as String)
                BufferObjectType.CLASS -> putObjectClass(value as Class<*>)
                BufferObjectType.PAIR -> putPair(value as Pair<*, *>)
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

        private const val COMPACT_FORMAT_VERSION = 2
        private const val COMPACT_HEADER_SIZE = Integer.BYTES + 1
        private const val COMPACT_LENGTH_FLAG = Int.MIN_VALUE

        private const val MAX_VAR_INT_BYTES = 5
        private const val MAX_VAR_LONG_BYTES = 10
        private const val MAX_COMPACT_REFERENCES = Int.MAX_VALUE - 8
        private const val MAX_COMPACT_STRING_BYTES = Int.MAX_VALUE ushr 1
        private const val MIN_INTERNED_STRING_LENGTH = 4
        private const val MAX_INTERNED_STRINGS = 4096

        private const val MAX_RETAINED_REFERENCE_CAPACITY = 1024
        private const val MAX_RETAINED_STRING_CAPACITY = 1024

        private const val COLLECTION_ARRAY_LIST = 0
        private const val COLLECTION_HASH_SET = 1
        private const val COLLECTION_LINKED_HASH_SET = 2
        private const val COLLECTION_OTHER = 3

        private const val MAP_HASH = 0
        private const val MAP_LINKED_HASH = 1
        private const val MAP_OTHER = 2

        private fun collectionCapacity(expectedSize: Int): Int = when {
            expectedSize < 3 -> expectedSize + 1
            expectedSize < 1 shl 30 -> (expectedSize / 0.75f + 1.0f).toInt()
            else -> Int.MAX_VALUE
        }

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
        fun toBuffer(any: Any, context: SchemaContext? = null): ByteBuffer =
            serializeFramed(any, context, compact = true)

        /** Writes the original version-1 format for migration tests or rolling upgrades. */
        @Throws(BufferingException::class)
        @JvmOverloads
        @JvmStatic
        fun toLegacyBuffer(any: Any, context: SchemaContext? = null): ByteBuffer =
            serializeFramed(any, context, compact = false)

        private fun serializeFramed(any: Any, context: SchemaContext?, compact: Boolean): ByteBuffer {
            val bufferStream = BufferStream(context)
            bufferStream.compactFormat = compact
            bufferStream.expandableByteBuffer!!.buffer.position(
                if (compact) COMPACT_HEADER_SIZE else Integer.BYTES
            )
            bufferStream.putObject(any)

            // Re-acquire the buffer because ExpandableByteBuffer may have replaced it while growing.
            val result = bufferStream.byteBuffer
            val serializedSize = result.position()
            require(serializedSize > 0) { "Serialized buffer must not be empty" }

            if (compact) {
                result.putInt(0, serializedSize or COMPACT_LENGTH_FLAG)
                result.put(Integer.BYTES, COMPACT_FORMAT_VERSION.toByte())
            } else {
                result.putInt(0, serializedSize)
            }
            result.flip()
            return result
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
            val bufferStartingPosition = buffer.position()
            val originalLimit = buffer.limit()
            if (buffer.remaining() < Integer.BYTES) {
                throw com.onyx.exception.BufferUnderflowException(
                    com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW
                )
            }

            val framedSize = buffer.int
            val compact = framedSize < 0
            val maxBufferSize = if (compact) framedSize and Int.MAX_VALUE else framedSize
            val minimumSize = if (compact) COMPACT_HEADER_SIZE else Integer.BYTES
            if (maxBufferSize < minimumSize || maxBufferSize > originalLimit - bufferStartingPosition) {
                throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE)
            }

            val recordEnd = bufferStartingPosition + maxBufferSize
            buffer.limit(recordEnd)
            try {
                if (compact) {
                    val version = buffer.get().toInt() and 0xff
                    if (version != COMPACT_FORMAT_VERSION) {
                        buffer.position(recordEnd)
                        throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE)
                    }
                }

                val bufferStream = BufferStream(buffer)
                bufferStream.context = context
                bufferStream.compactFormat = compact
                bufferStream.expandableByteBuffer = ExpandableByteBuffer(buffer, maxBufferSize, bufferStartingPosition)
                bufferStream.isComingFromBuffer = true

                val returnValue: Any? = try {
                    bufferStream.value
                } catch (e: BufferingException) {
                    buffer.position(recordEnd)
                    throw e
                } catch (e: Exception) {
                    buffer.position(recordEnd)
                    if (e is BufferUnderflowException) {
                        throw com.onyx.exception.BufferUnderflowException(
                            com.onyx.exception.BufferUnderflowException.BUFFER_UNDERFLOW
                        )
                    }
                    throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE, null, e)
                }

                if (buffer.position() != recordEnd) {
                    buffer.position(recordEnd)
                    throw BufferingException(BufferingException.UNKNOWN_DESERIALIZE)
                }
                return returnValue
            } finally {
                val finalPosition = minOf(buffer.position(), recordEnd)
                buffer.limit(originalLimit)
                buffer.position(finalPosition)
            }
        }
    }
}
