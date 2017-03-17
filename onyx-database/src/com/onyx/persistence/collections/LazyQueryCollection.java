package com.onyx.persistence.collections;


import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.BufferingException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.record.AbstractRecordController;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;

import java.util.*;
import java.util.function.Consumer;

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
 * <pre>
 * <code>
 *     PersistenceManager manager = factory.getPersistenceManager();
 *
 *     Query query = new Query();
 *     query.setEntityType(MyEntity.class);
 *     List myResults = manager.executeLazyQuery(query); // Returns an instance of LazyQueryCollection
 *
 * </code>
 * </pre>
 *
 */
@SuppressWarnings("unchecked")
public class LazyQueryCollection<E> extends AbstractList<E> implements List<E>, BufferStreamable {

    @SuppressWarnings("WeakerAccess")
    protected List<Object> identifiers = null;

    @SuppressWarnings("WeakerAccess")
    transient protected CompatMap<Object, IManagedEntity> values = new CompatWeakHashMap<>();
    @SuppressWarnings("WeakerAccess")
    transient protected EntityDescriptor entityDescriptor = null;
    @SuppressWarnings("WeakerAccess")
    transient protected PersistenceManager persistenceManager = null;
    @SuppressWarnings("WeakerAccess")
    transient protected String contextId;

    @SuppressWarnings("unused")
    public LazyQueryCollection()
    {

    }

    /**
     * Constructor
     *
     * @param entityDescriptor Entity Descriptor for records
     * @param identifiers Map of Identifiers
     * @param context Schema Context
     */
    public LazyQueryCollection(EntityDescriptor entityDescriptor, Map identifiers, SchemaContext context)
    {
        this.contextId = context.getContextId();

        this.persistenceManager = context.getSerializedPersistenceManager();
        this.identifiers = new ArrayList<>(identifiers.keySet());
        this.entityDescriptor = entityDescriptor;
    }

    /**
     * Quantity or record references within the List
     *
     * @since 1.0.0
     *
     * @return Size of the List
     */
    @Override
    public int size()
    {
        return identifiers.size();
    }

    /**
     * Boolean key indicating whether the list is empty
     *
     * @since 1.0.0
     *
     * @return (size equals 0)
     */
    @Override
    public boolean isEmpty()
    {
        return (identifiers.size() == 0);
    }

    /**
     * Contains an object and is initialized
     *
     * @since 1.0.0
     *
     * @param o Object to check
     * @return Boolean
     */
    @Override
    public boolean contains(Object o)
    {
        Object identifier;
        try
        {
            identifier = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) o, entityDescriptor.getIdentifier());
        } catch (EntityException e)
        {
            return false;
        }
        return identifiers.contains(identifier);
    }

    /**
     * Add an element to the lazy collection
     *
     * <pre>
     * This must add a managed entity
     * </pre>
     *
     * @since 1.0.0
     *
     * @param e Record that implements ManagedEntity
     * @return Added or not
     */
    @Override
    public boolean add(E e)
    {
        throw new RuntimeException("Method unsupported");
    }

    /**
     * Remove all objects
     *
     * @since 1.0.0
     *
     */
    @Override
    public void clear()
    {
        values.clear();
        identifiers.clear();
    }

    /**
     * Get object at index and initialize it if it does not exist
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return ManagedEntity
     */
    @Override
    public E get(int index)
    {
        IManagedEntity entity = values.get(index);
        if (entity == null)
        {
            try
            {
                entity = persistenceManager.getWithReferenceId(entityDescriptor.getClazz(), (long)identifiers.get(index));
            } catch (EntityException e)
            {
                return null;
            }
            values.put(index, entity);
        }
        return (E) entity;
    }

    /**
     * Get object at index and initialize it if it does not exist
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return ManagedEntity
     */
    public Map getDict(int index)
    {
        try
        {
            return persistenceManager.getMapWithReferenceId(entityDescriptor.getClazz(), (long)identifiers.get(index));
        } catch (EntityException e)
        {
            return null;
        }
    }

    /**
     * Set object at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element ManagedEntity
     * @return Record set
     */
    @Override
    public E set(int index, E element)
    {
        throw new RuntimeException("Method unsupported");
    }

    /**
     * Add object at index
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element ManagedEntity
     */
    @Override
    public void add(int index, E element)
    {
        this.set(index, element);
    }

    /**
     * Remove object at index
     *
     *
     * @since 1.0.0
     *
     * @param index Record Index
     * @return ManagedEntity removed
     */
    @Override
    public E remove(int index)
    {
        Object existingObject = identifiers.get(index);
        identifiers.remove(existingObject);
        return (E) values.remove(existingObject);
    }

    /**
     * Remove object at index
     *
     *
     * @since 1.0.0
     *
     * @param o ManagedEntity
     * @return Boolean Value
     */
    @Override
    public boolean remove(Object o)
    {
        Object identifier;
        try
        {
            identifier = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) o, entityDescriptor.getIdentifier());
        } catch (AttributeMissingException e)
        {
            return false;
        }
        values.remove(identifier);
        return identifiers.remove(identifier);
    }


    @SuppressWarnings("WeakerAccess")
    public List<Object> getIdentifiers()
    {
        return identifiers;
    }

    @SuppressWarnings("unused")
    public void setIdentifiers(List<Object> identifiers)
    {
        this.identifiers = identifiers;
    }


    @SuppressWarnings("WeakerAccess")
    public EntityDescriptor getEntityDescriptor()
    {
        return entityDescriptor;
    }

    @SuppressWarnings("unused")
    public void setEntityDescriptor(EntityDescriptor entityDescriptor)
    {
        this.entityDescriptor = entityDescriptor;
//        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    @Override
    public void read(BufferStream bufferStream) throws BufferingException {
        this.values = new CompatWeakHashMap<>();
        this.identifiers = (List) bufferStream.getCollection();
        String className = bufferStream.getString();
        this.contextId = bufferStream.getString();


        SchemaContext context = DefaultSchemaContext.registeredSchemaContexts.get(contextId);
        if(context == null)
        {
            context = (SchemaContext)DefaultSchemaContext.registeredSchemaContexts.values().toArray()[0];
        }
        try {
            this.entityDescriptor = context.getBaseDescriptorForEntity(Class.forName(className));
        } catch (EntityException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        this.persistenceManager = context.getSystemPersistenceManager();
    }

    @Override
    public void write(BufferStream bufferStream) throws BufferingException {
        bufferStream.putCollection(this.getIdentifiers());
        bufferStream.putString(this.getEntityDescriptor().getClazz().getName());
        bufferStream.putString(contextId);
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     * <p>
     * <p>The returned iterator is <a href="#fail-fast"><i>fail-fast</i></a>.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < size();
            }

            @Override
            public E next() {
                try {
                    return get(i);
                } finally {
                    i++;
                }
            }
        };
    }

    /**
     * For Each This is overridden to utilize the iterator
     *
     * @param action consumer to apply each iteration
     */
    @SuppressWarnings("WhileLoopReplaceableByForEach")
    @Override
    public void forEach(Consumer<? super E> action) {
        Iterator<E> iterator = iterator();
        while(iterator.hasNext())
        {
            action.accept(iterator.next());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns {@code listIterator(0)}.
     *
     * @see #listIterator(int)
     */
    public ListIterator<E> listIterator() {
        throw new RuntimeException("Method unsupported, hydrate relationship using initialize before using listIterator");
    }
}
