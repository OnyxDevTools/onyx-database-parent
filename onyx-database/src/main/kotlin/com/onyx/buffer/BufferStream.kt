ChatGPT Pro




Today 8:33 PM

Pasted markdown(2).md
File
the most expensive part of my app is managing references.  Is there any way to optimize this while maintaining intent


Worked for 10m 30s
Yes. There is a large, low-risk optimization hiding in plain sight
Every call to putObject currently probes this nested structure:

HashMap<Class<*>, HashMap<Any, Int>>
That happens regardless of whether the value’s type is ever reference-tracked.


But on the writer side, only these values are ever added to the reference table:

CLASS

PAIR

OTHER

Classes and pairs register themselves here, while reflected OTHER objects register before their fields are written.


Everything else is paying a toll at a reference booth it can never pass through.

Safest optimization: only check referenceable types
Replace this:

var bufferObjectType = BufferObjectType.getTypeCodeForClass(value, context)
val referenceNumber = referenceIndex(value).toShort()
if (referenceNumber > -1)
bufferObjectType = BufferObjectType.REFERENCE
With this:

var bufferObjectType = BufferObjectType.getTypeCodeForClass(value, context)
var referenceNumber = -1

if (
value != null &&
when (bufferObjectType) {
    BufferObjectType.CLASS,
    BufferObjectType.PAIR,
    BufferObjectType.OTHER -> true

    else -> false
}
) {
    referenceNumber = referenceIndex(value)

    if (referenceNumber >= 0)
        bufferObjectType = BufferObjectType.REFERENCE
}
Then change the reference branch from:

BufferObjectType.REFERENCE -> putShort(referenceNumber)
To:

BufferObjectType.REFERENCE -> putShort(referenceNumber.toShort())
This eliminates reference-map activity for:

Primitive wrappers

Strings

Dates

Enums

Entities

Buffered values

Arrays

Collections

Maps

Nulls

The registration order, reference numbers, and serialized format remain unchanged. Only lookups that could never succeed are removed.

If 90 percent of your putObject calls are attributes such as strings, numbers, booleans, arrays, or collection elements, this removes roughly 90 percent of the calls into referenceIndex.

Also replace getOrPut during registration
Your current registration performs nested getOrPut operations:

references!!.getOrPut(reference.javaClass) { HashMap() }
.getOrPut(reference) {
    if (referenceCount == Short.MAX_VALUE.toInt())
        return

    ++referenceCount
}
A more direct replacement is:

private fun addReference(reference: Any) {
    if (isComingFromBuffer) {
        val referenceIndex = reserveReference()

        if (referenceIndex > 0)
            referencesByIndex!![referenceIndex - 1] = reference

        return
    }

    if (referenceCount >= Short.MAX_VALUE.toInt())
        return

    val byClass = references
        ?: HashMap<Class<*>, HashMap<Any, Int>>().also {
            references = it
        }

    val clazz = reference.javaClass

    val classMap = byClass[clazz]
        ?: HashMap<Any, Int>().also {
            byClass[clazz] = it
        }

    val nextIndex = referenceCount + 1

    if (classMap.putIfAbsent(reference, nextIndex) == null)
        referenceCount = nextIndex
}
This preserves your existing class-scoped equality behavior but avoids the inner getOrPut lookup-plus-insertion path and its lambda.

The deeper issue: these are value references, not object references
Your reference table uses hashCode() and equals(), and your documentation explicitly requires those methods for reference recognition.


That means this:

val first = Customer(id = 10)
val second = Customer(id = 10)
can deserialize as though both positions referred to the same object instance, provided first == second.

That is value interning, not conventional object-reference preservation.

It can also be expensive. A generated data-class hashCode() may traverse strings, lists, nested objects, and other fields. For a large graph, reference checking can become a shadow serialization pass that repeatedly walks the same branches.

When the intent is actual shared-instance and cycle preservation
Use one IdentityHashMap:

private var references: IdentityHashMap<Any, Int>? = null

private fun referenceIndex(reference: Any?): Int {
    if (reference == null)
        return -1

    return references?.get(reference) ?: -1
}
Writer registration becomes:

private fun addWriterReference(reference: Any) {
    if (referenceCount >= Short.MAX_VALUE.toInt())
        return

    val referenceMap = references
        ?: IdentityHashMap<Any, Int>().also {
            references = it
        }

    if (referenceMap[reference] == null)
        referenceMap[reference] = ++referenceCount
}
This eliminates:

The outer class map

Domain-object hashCode()

Domain-object equals()

Accidental merging of distinct but equal mutable objects

It retains repeated-instance and circular-reference behavior. Existing readers can still read the resulting format, but equal distinct objects will no longer be collapsed, so this does not preserve your current value-deduplication semantics.

When exact value-deduplication semantics must remain
After applying the gated lookup, the next serious optimization would be a custom open-addressed reference table with:

Array<Any?>       // stored references
IntArray          // precomputed hashes
IntArray          // unboxed reference indexes
Equality would remain:

existing.javaClass === candidate.javaClass &&
(existing === candidate || candidate == existing)
That preserves your current class-scoped value equality while eliminating:

One inner HashMap per encountered class

Boxed Int reference indexes

HashMap node allocations

Recalculation of stored-object hashes during table resizing

Two separate map traversals

That is worthwhile only if CLASS/PAIR/OTHER reference handling remains hot after the first patch.

One existing reference-semantics gap
Arrays, collections, and maps are not currently registered as references before their contents are serialized, and their reader methods do not reserve reference slots before reading those contents.


Consequently:

val list = mutableListOf<Any>()
list += list
cannot currently round-trip as a cyclic collection, and repeated uses of the same list or map are serialized repeatedly.

The gated optimization deliberately preserves that current behavior. It does not expand reference tracking and therefore does not add more bookkeeping.

Recommended sequence
First apply the type-gated lookup. It is the strongest guaranteed improvement and leaves reference numbering and wire behavior intact.

Then use the putIfAbsent registration change.

If profiling still lands heavily in domain hashCode() or equals(), the real decision is semantic: use IdentityHashMap for true object references, or use a custom primitive open-addressed table when equal-value deduplication must remain.


can you provide me a drop in replacement

Worked for 12m 38s
Drop-in replacement
Download the optimized BufferStream.kt

Replace the existing file directly. This version:

Skips reference-table lookup for every type except CLASS, PAIR, and OTHER, which are the only values currently registered as references.


Replaces the nested writer-side getOrPut registration with direct map initialization and putIfAbsent.


Preserves the existing class-scoped, value-based equals/hashCode reference semantics.

Preserves reference numbering and the serialized wire format.

Does not switch to identity references or change array, collection, map, entity, or buffered-object behavior.

The main savings come from eliminating the nested reference lookup and its possible hashCode()/equals() work from strings, numbers, dates, enums, arrays, collections, maps, entities, nulls, and other untracked types. The original implementation performed that lookup unconditionally for every putObject call.


I also passed the replacement through kotlinc parsing. A full compile requires the rest of the Onyx project dependencies, which were not included with the uploaded file.


BufferStream.kt
Code


Library
/
BufferStream.kt


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

    // References by class and value hash.
    private var references: HashMap<Class<*>, HashMap<Any, Int>>? = null

    // References by index number ordered by first used
    private var referencesByIndex: ArrayList<Any?>? = null

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
        // Reader-side references are indexed by insertion order because objects may not be fully hydrated
        // and therefore may not have stable hashCode/equals implementations yet.
        if (isComingFromBuffer) {
            val referenceIndex = reserveReference()
            if (referenceIndex > 0)
                referencesByIndex!![referenceIndex - 1] = reference
            return
        }

        if (referenceCount >= Short.MAX_VALUE.toInt())
            return

        val referenceMap = references
            ?: HashMap<Class<*>, HashMap<Any, Int>>().also { references = it }
        val referenceClass = reference.javaClass
        val classMap = referenceMap[referenceClass]
            ?: HashMap<Any, Int>().also { referenceMap[referenceClass] = it }
        val nextReference = referenceCount + 1

        // putIfAbsent performs a single hash-table traversal while preserving the existing
        // class-scoped hashCode/equals reference semantics.
        if (classMap.putIfAbsent(reference, nextReference) == null)
            referenceCount = nextReference
    }

    /**
     * Reserve the next reader-side reference slot before reading nested values.
     * The writer registers pairs before their elements, so the reader must do the same.
     */
    private fun reserveReference(): Int {
        if (referenceCount == Short.MAX_VALUE.toInt())
            return -1

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

        val classMap = references?.get(reference.javaClass) ?: return -1
        return classMap[reference] ?: return -1
    }

    /**
     * Reference of the reference index
     * @param index Index to seek to
     * @return The actual value referenced
     */
    private fun referenceOf(index: Int): Any = referencesByIndex?.getOrNull(index - 1)!!

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
        references?.clear()
        if ((referencesByIndex?.size ?: 0) > MAX_RETAINED_REFERENCE_CAPACITY)
            referencesByIndex = null
        else
            referencesByIndex?.clear()
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
            beginReading()
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

            val collectionClass = value as Class<*>?
            val size = expandableByteBuffer!!.buffer.int
            val initialCapacity = size.coerceAtLeast(0)

            val collection = try {
                when {
                    collectionClass == ArrayList::class.java || collectionClass == SortedList::class.java || Modifier.isPrivate(collectionClass!!.modifiers) ->
                        ArrayList(initialCapacity)
                    collectionClass == HashSet::class.java ->
                        HashSet(collectionCapacity(initialCapacity))
                    collectionClass == LinkedHashSet::class.java ->
                        LinkedHashSet(collectionCapacity(initialCapacity))
                    else -> collectionClass.instance<MutableCollection<Any?>>(context?.contextId ?: "")
                }
            } catch (e: Exception) {
                ArrayList(initialCapacity)
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
            val mapSize = expandableByteBuffer!!.buffer.int
            val initialCapacity = collectionCapacity(mapSize.coerceAtLeast(0))
            val map:MutableMap<Any,Any?> = try {
                when (mapClass) {
                    HashMap::class.java -> HashMap(initialCapacity)
                    LinkedHashMap::class.java -> LinkedHashMap(initialCapacity)
                    else -> mapClass.instance(context?.contextId ?: "")
                }
            } catch (e: InstantiationException) {
                throw BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass)
            } catch (e: IllegalAccessException) {
                throw BufferingException(BufferingException.CANNOT_INSTANTIATE, mapClass)
            }

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
                expandableByteBuffer!!.ensureRequiredSize(arr.size)
                expandableByteBuffer!!.buffer.get(arr)
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
    fun putArray(array: Any?) {
        when {
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
                expandableByteBuffer!!.ensureSize(arr.size)
                expandableByteBuffer!!.buffer.put(arr)
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
                for (anArr in arr) expandableByteBuffer!!.buffer.putDouble(anArr)
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
        var referenceNumber = -1

        // Only CLASS, PAIR, and OTHER values are registered by the writer. Avoid hashing and
        // probing the reference maps for every scalar, string, date, array, collection, and map.
        when (bufferObjectType) {
            BufferObjectType.CLASS,
            BufferObjectType.PAIR,
            BufferObjectType.OTHER -> {
                referenceNumber = referenceIndex(value)
                if (referenceNumber >= 0)
                    bufferObjectType = BufferObjectType.REFERENCE
            }
            else -> Unit
        }

        try {

            // Put the serializer type
            putByte(bufferObjectType.ordinal.toByte())

            when (bufferObjectType) {
                BufferObjectType.NULL -> return this.expandableByteBuffer!!.buffer.position() - position
                BufferObjectType.REFERENCE -> putShort(referenceNumber.toShort())
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

        private const val MAX_RETAINED_REFERENCE_CAPACITY = 1024

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
            val bufferStartingPosition = buffer.position()
            val maxBufferSize = buffer.int

            val bufferStream = BufferStream(buffer)
            bufferStream.context = context

            bufferStream.expandableByteBuffer = ExpandableByteBuffer(buffer, maxBufferSize, bufferStartingPosition)
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
