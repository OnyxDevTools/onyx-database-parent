package com.onyx.persistence.collections

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.BufferingException
import com.onyx.exception.OnyxException
import com.onyx.extension.common.metadata
import com.onyx.extension.identifier
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager

import java.util.*
import java.util.function.Consumer

/**
 * LazyQueryCollection is used to return query results that are lazily instantiated.
 *
 * If by calling executeLazyQuery it will return a list with record references.  The references are then used to hydrate the results when referenced through the List interface methods.
 *
 * Also, this is not available using the Web API.  It is a limitation due to the JSON serialization.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * Query query = new Query();
 * query.setEntityType(MyEntity.class);
 * List myResults = manager.executeLazyQuery(query); // Returns an instance of LazyQueryCollection
 *
 */
class LazyQueryCollection<E : IManagedEntity> () : AbstractList<E>(), List<E>, BufferStreamable {

    @Transient private var values: MutableMap<Any, E?> = WeakHashMap()
    @Transient
    private lateinit var contextId: String
    @Transient private var persistenceManager: PersistenceManager? = null
    @Transient lateinit var entityDescriptor: EntityDescriptor

    lateinit var identifiers: MutableList<Reference>

    private var hasSelections = false

    constructor(entityDescriptor: EntityDescriptor, references: List<Reference>, context: SchemaContext):this() {
        this.entityDescriptor = entityDescriptor
        this.contextId = context.contextId
        this.persistenceManager = context.serializedPersistenceManager
        this.identifiers = synchronized(references) { ArrayList(references) }
    }

    /**
     * Quantity or record references within the List
     *
     * @since 1.0.0
     *
     * @return Size of the List
     */
    override val size: Int
        get() = identifiers.size

    /**
     * Boolean key indicating whether the list is empty
     *
     * @since 1.0.0
     *
     * @return (size equals 0)
     */
    override fun isEmpty(): Boolean = identifiers.isEmpty()

    /**
     * Contains an value and is initialized
     *
     * @since 1.0.0
     *
     * @param element Object to check
     * @return Boolean
     */
    override operator fun contains(element: E): Boolean = identifiers.contains((element as IManagedEntity).identifier(descriptor = entityDescriptor))

    /**
     * Add an element to the lazy collection
     *
     * This must add a managed entity
     *
     * @since 1.0.0
     *
     * @param element Record that implements ManagedEntity
     * @return Added or not
     */
    override fun add(element: E?): Boolean = throw RuntimeException("Method unsupported")

    /**
     * Remove all objects
     *
     * @since 1.0.0
     */
    override fun clear() {
        values.clear()
        identifiers.clear()
    }

    /**
     * Get value at index and initialize it if it does not exist
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return ManagedEntity
     */
    override fun get(index: Int): E {
        var entity: E? = values[index]
        if (entity == null) {
            entity = try {
            val reference = identifiers[index]
                persistenceManager!!.getWithReference(entityDescriptor.entityClass, reference)

            } catch (e: OnyxException) {
                null
            }

            values[index] = entity
        }
        return entity!!
    }

    /**
     * Get value at index and initialize it if it does not exist
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return ManagedEntity
     */
    @Suppress("UNCHECKED_CAST")
    fun getDict(index: Int): Map<String, Any?>? = try {
        persistenceManager!!.getMapWithReferenceId(entityDescriptor.entityClass, identifiers[index])
    } catch (e: OnyxException) {
        null
    }

    /**
     * Set value at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element ManagedEntity
     * @return Record set
     */
    override fun set(index: Int, element: E?): E = throw RuntimeException("Method unsupported")

    /**
     * Add value at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element ManagedEntity
     */
    override fun add(index: Int, element: E?) {
        this[index] = element
    }

    /**
     * Remove value at index
     *
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return ManagedEntity removed
     */
    override fun removeAt(index: Int): E {
        val existingObject = identifiers[index]
        identifiers.remove(existingObject)
        return values.remove(existingObject) as E
    }

    override fun remove(element: E): Boolean {
        val identifier: Any? = (element as IManagedEntity?)!!.identifier(descriptor = entityDescriptor)
        values.remove(identifier)
        return identifiers.remove(identifier)
    }

    @Throws(BufferingException::class)
    @Suppress("UNCHECKED_CAST")
    override fun read(buffer: BufferStream) {
        this.values = WeakHashMap()
        this.identifiers = buffer.collection as MutableList<Reference>
        val className = buffer.string
        this.contextId = buffer.string
        this.hasSelections = buffer.boolean

        val context = Contexts.get(contextId) ?: Contexts.firstRemote()

        this.entityDescriptor = context.getBaseDescriptorForEntity(metadata(this.contextId).classForName(className, context))!!
        this.persistenceManager = context.systemPersistenceManager
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putCollection(this.identifiers)
        buffer.putString(this.entityDescriptor.entityClass.name)
        buffer.putString(contextId)
        buffer.putBoolean(hasSelections)
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        this.read(buffer)
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) {
        this.write(buffer)
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * The returned iterator is [*fail-fast*](#fail-fast).
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        var i = 0

        override fun hasNext(): Boolean = i < size

        override fun next(): E = try {
            get(i)
        } finally {
            i++
        }

        override fun remove() = throw RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator.remove")
    }

    /**
     * For Each This is overridden to utilize the iterator
     *
     * @param action consumer to apply each iteration
     */
    override fun forEach(action: Consumer<in E>) {
        val iterator = iterator()
        while (iterator.hasNext()) {
            action.accept(iterator.next())
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun listIterator(): MutableListIterator<E> = object : MutableListIterator<E> {
        var i = -1

        override fun hasNext(): Boolean = i + 1 < size

        override fun next(): E = try {
            get(i)
        } finally {
            i++
        }

        override fun hasPrevious(): Boolean = i > 0

        override fun previous(): E = try {
            get(i)
        } finally {
            i--
        }

        override fun nextIndex(): Int = i + 1

        override fun previousIndex(): Int = i - 1

        override fun remove() = throw RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator.remove")

        override fun set(element: E) = throw RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator.set")

        override fun add(element: E) = throw RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator.add")
    }
}
