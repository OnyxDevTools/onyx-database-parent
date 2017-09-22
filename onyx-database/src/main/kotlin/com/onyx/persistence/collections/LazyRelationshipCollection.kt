package com.onyx.persistence.collections

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.BufferingException
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.extension.toRelationshipReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.interactors.relationship.data.RelationshipReference
import com.onyx.util.map.CompatWeakHashMap

import java.util.*
import java.util.function.Consumer

/**
 * LazyRelationshipCollection is used to return references for a ManagedObject's relationships
 *
 * This is returned if the FetchPolicy is indicated as Lazy for a relationship
 *
 * Also, this is not available using the Web API.  It is a limitation due to the JSON serialization.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * @Entity
 * public MyEntity extends ManagedEntity
 * {
 *      @Identifier
 *      @Attribute
 *      public long myId;
 *
 *      @Relationship(fetchPolicy = FetchPolicy.LAZY, inverse = MyChildEntity.class)
 *      public List<MyChildEntity> children;
 * }
 *
 * ...
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 * MyChildEntity entity = manager.find(MyChildEntity.class, 1);
 *
 * entity.children; // Instance of LazyRelationshipCollection
 *
 */
class LazyRelationshipCollection<E : IManagedEntity>()  : AbstractList<E>(), MutableList<E>, BufferStreamable {


    @Transient private var values: MutableMap<Int, E?> = WeakHashMap()
    @Transient private var persistenceManager: PersistenceManager? = null
    @Transient lateinit var entityDescriptor: EntityDescriptor
    private var contextId: String? = null
    lateinit var identifiers: MutableList<RelationshipReference>

    constructor(entityDescriptor: EntityDescriptor, identifiers: MutableList<RelationshipReference>, context: SchemaContext):this() {
        this.persistenceManager = context.systemPersistenceManager
        this.entityDescriptor = entityDescriptor
        this.identifiers = identifiers
        this.entityDescriptor = entityDescriptor
        this.contextId = context.contextId
    }

    /**
     * Size of record references
     *
     * @since 1.0.0
     *
     * @return Size of References
     */
    override val size: Int
        get() = identifiers.size

    /**
     * Collection is Empty
     *
     * @since 1.0.0
     *
     * @return Flag for indicating Collection is empty ( longSize == 0 )
     */
    override fun isEmpty(): Boolean = identifiers.size == 0

    /**
     * Contains an object and is initialized
     *
     * @since 1.0.0
     *
     * @param element Record to check
     * @return Flag indicating record exists within collection
     */
    override operator fun contains(element: E): Boolean = identifiers.contains(element.toRelationshipReference(Contexts.get(contextId!!)!!))

    /**
     * Add an element to the lazy collection
     * This must add a managed entity
     *
     * @since 1.0.0
     *
     * @param element Record Managed Entity
     * @return Flag indicating added or not
     */
    override fun add(element: E): Boolean = throw RuntimeException("Method unsupported, hydrate relationship using initialize before modifying")

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
     * Get object at index and initialize it if it does not exist
     *
     * @since 1.0.0
     *
     * @param index Record index
     * @return ManagedEntity
     */
    override fun get(index: Int): E {
        var entity: E? = values[index]
        if (entity == null) {
            entity = try {
                val ref = identifiers[index]
                persistenceManager!!.findByIdWithPartitionId(entityDescriptor.entityClass, ref.identifier!!, ref.partitionId)
            } catch (e: OnyxException) {
                null
            }

            values.put(index, entity)
        }
        return entity!!
    }

    /**
     * Set object at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element Record
     * @return Record set at index
     */
    override fun set(index: Int, element: E): E = throw RuntimeException("Method unsupported, hydrate relationship using initialize before modifying")

    /**
     * Add object at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element Record
     */
    override fun add(index: Int, element: E) {
        this[index] = element
    }

    /**
     * Remove object at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return Record removed
     */
    override fun removeAt(index: Int): E {
        values.remove(index)
        val reference = identifiers.removeAt(index)
        @Suppress("UNCHECKED_CAST")
        return reference.toManagedEntity(Contexts.get(contextId!!)!!, entityDescriptor.entityClass) as E
    }

    /**
     * Remove object at index
     *
     * @since 1.0.0
     *
     * @param element Record
     * @return If record was removed
     */
    override fun remove(element: E): Boolean = throw RuntimeException("Method unsupported, hydrate relationship using initialize before modifying")

    @Suppress("UNCHECKED_CAST")
    @Throws(BufferingException::class)
    override fun read(bufferStream: BufferStream) {
        this.values = CompatWeakHashMap()
        this.identifiers = bufferStream.collection as MutableList<RelationshipReference>
        val className = bufferStream.string
        this.contextId = bufferStream.string

        val context = Contexts.get(contextId!!) ?: Contexts.first()
        this.entityDescriptor = context.getBaseDescriptorForEntity(Class.forName(className))!!
        this.persistenceManager = context.systemPersistenceManager
    }

    @Throws(BufferingException::class)
    override fun write(bufferStream: BufferStream) {
        bufferStream.putCollection(identifiers)
        bufferStream.putString(this.entityDescriptor.entityClass.name)
        bufferStream.putString(this.contextId)
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     *
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {
            override fun remove() = throw RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator")

            internal var i = 0

            override fun hasNext(): Boolean = i < size

            override fun next(): E {
                try {
                    return get(i)
                } finally {
                    i++
                }
            }
        }
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
     *
     *
     * This implementation returns `listIterator(0)`.
     *
     * @see .listIterator
     */
    override fun listIterator(): MutableListIterator<E> = throw RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator")

}
