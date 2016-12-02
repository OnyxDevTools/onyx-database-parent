package com.onyx.persistence.collections;


import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.BufferingException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

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
public class LazyQueryCollection<E> extends ArrayList<E> implements List<E>, Externalizable, BufferStreamable {

    protected List<Object> identifiers = null;

    transient protected Map<Object, IManagedEntity> values = new WeakHashMap<>();
    transient protected EntityDescriptor entityDescriptor = null;
    transient protected PersistenceManager persistenceManager = null;
    transient protected PartitionContext partitionContext = null;
    transient protected SchemaContext context;

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
        this.context = context;

        this.persistenceManager = context.getSerializedPersistenceManager();
        this.identifiers = new ArrayList<>(identifiers.keySet());
        this.entityDescriptor = entityDescriptor;
        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    /**
     * Constructor
     *
     * @param entityDescriptor EntityDescriptor for records
     * @param identifiers List of identifiers
     * @param context Schema Context
     */
    public LazyQueryCollection(EntityDescriptor entityDescriptor, List<Object> identifiers, SchemaContext context)
    {
        this.context = context;
        this.persistenceManager = context.getSerializedPersistenceManager();
        this.identifiers = new ArrayList<>(identifiers);
        this.entityDescriptor = entityDescriptor;
        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    /**
     * Quantity or record references within the List
     *
     * @author Tim Osborn
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
     * Boolean value indicating whether the list is empty
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @return (size == 0)
     */
    @Override
    public boolean isEmpty()
    {
        return (identifiers.size() == 0);
    }

    /**
     * Contains an object and is initialized
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param o Object to check
     * @return Boolean
     */
    @Override
    public boolean contains(Object o)
    {
        Object identifier = null;
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
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param e Record that implements ManagedEntity
     * @return Added or not
     */
    @Override
    public boolean add(E e)
    {
        try
        {
            Object identifier = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) e, entityDescriptor.getIdentifier());
            Object partitionId = partitionContext.getPartitionId((IManagedEntity) e);
            IManagedEntity entity = persistenceManager.findByIdInPartition(e.getClass(), identifier, partitionId);
            values.put(identifier, entity);
            identifiers.add(identifier);
            return true;
        } catch (EntityException e1)
        {
            return false;
        }
    }

    /**
     * Remove all objects
     *
     * @author Tim Osborn
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
     * @author Tim Osborn
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
     * @author Tim Osborn
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
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element ManagedEntity
     * @return Record set
     */
    @Override
    public E set(int index, E element)
    {
        try
        {

            Object identifier = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) element, entityDescriptor.getIdentifier());
            Object partitionId = partitionContext.getPartitionId((IManagedEntity)element);
            IManagedEntity entity = persistenceManager.findByIdInPartition(element.getClass(), identifier, partitionId);

            Object existingObject = identifiers.get(index);
            values.remove(existingObject);
            values.put(identifier, entity);
            identifiers.set(index, identifier);
            return element;
        } catch (EntityException e1)
        {
            return null;
        }
    }

    /**
     * Add object at index
     *
     * @author Tim Osborn
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
     * @author Tim Osborn
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
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param o ManagedEntity
     * @return Boolean Value
     */
    @Override
    public boolean remove(Object o)
    {
        Object identifier = null;
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


    public List<Object> getIdentifiers()
    {
        return identifiers;
    }

    public void setIdentifiers(List<Object> identifiers)
    {
        this.identifiers = identifiers;
    }


    public EntityDescriptor getEntityDescriptor()
    {
        return entityDescriptor;
    }

    public void setEntityDescriptor(EntityDescriptor entityDescriptor)
    {
        this.entityDescriptor = entityDescriptor;
        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    /**
     * Externalize for serialization with use in RMI Server
     * @param out Object Output Stream.  Most likely a SocketOutputStream
     *
     * @throws IOException
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(this.getIdentifiers());
        out.writeUTF(this.getEntityDescriptor().getClazz().getName());
        out.writeUTF(this.context.getContextId());
    }

    /**
     * Read from the stream source.  Most likely used with the RMI Server
     * @param in Input Stream aka SocketObjectInputStream
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.identifiers = (List) in.readObject();
        String className = in.readUTF();
        String contextId = in.readUTF();

        this.context = DefaultSchemaContext.registeredSchemaContexts.get(contextId);
        this.entityDescriptor = context.getBaseDescriptorForEntity(Class.forName(className));
        this.partitionContext = new PartitionContext(context, entityDescriptor);
        this.persistenceManager = this.context.getSystemPersistenceManager();
    }

    @Override
    public void read(BufferStream bufferStream) throws BufferingException {
        this.values = new WeakHashMap<>();
        this.identifiers = (List) bufferStream.getCollection();
        String className = bufferStream.getString();
        String contextId = bufferStream.getString();

        this.context = DefaultSchemaContext.registeredSchemaContexts.get(contextId);
        try {
            this.entityDescriptor = context.getBaseDescriptorForEntity(Class.forName(className));
        } catch (EntityException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        this.partitionContext = new PartitionContext(context, entityDescriptor);
        this.persistenceManager = this.context.getSystemPersistenceManager();
    }

    @Override
    public void write(BufferStream bufferStream) throws BufferingException {
        bufferStream.putCollection(this.getIdentifiers());
        bufferStream.putString(this.getEntityDescriptor().getClazz().getName());
        bufferStream.putString(this.context.getContextId());
    }
}
