package com.onyx.persistence.collections;


import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.BufferingException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.record.AbstractRecordController;
import com.onyx.relationship.RelationshipReference;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

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
 * <pre>
 * <code>
 *
 *   {@literal @}Entity
 *     public MyEntity extends ManagedEntity
 *     {
 *        {@literal @}Identifier
 *        {@literal @}Attribute
 *         public long myId;
 *
 *        {@literal @}Relationship(fetchPolicy = FetchPolicy.LAZY, inverse = MyChildEntity.class)
 *         public List{@literal <}MyChildEntity{@literal >} children;
 *    }
 *
 *     ...
 *
 *    PersistenceManager manager = factory.getPersistenceManager();
 *    MyChildEntity entity = manager.find(MyChildEntity.class, 1);
 *
 *    entity.children; // Instance of LazyRelationshipCollection
 *
 * </code>
 * </pre>
 *
 */
public class LazyRelationshipCollection<E> extends ArrayList<E> implements List<E>, Externalizable, BufferStreamable {

    protected List<RelationshipReference> identifiers = null;
    transient protected EntityDescriptor entityDescriptor = null;

    transient protected Map<Object, IManagedEntity> values = new WeakHashMap<>();
    transient protected SchemaContext context = null;
    transient protected PartitionContext partitionContext = null;
    transient protected PersistenceManager persistenceManager;

    public LazyRelationshipCollection()
    {

    }

    /**
     * Constructor
     *
     * @param entityDescriptor Record Entity Descriptor
     * @param identifiers Map of Identifiers
     * @param context Schema Context
     */
    public LazyRelationshipCollection(EntityDescriptor entityDescriptor, Map<Object, Object> identifiers, SchemaContext context)
    {
        this.persistenceManager = context.getSystemPersistenceManager();
        this.identifiers = new ArrayList(identifiers.keySet());
        this.entityDescriptor = entityDescriptor;
        this.context = context;
        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    /**
     * Constructor
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param entityDescriptor  Record Entity Descriptor
     * @param identifiers Set of References
     * @param context Schema Context
     */
    public LazyRelationshipCollection(EntityDescriptor entityDescriptor, Set<Object> identifiers, SchemaContext context)
    {
        this.persistenceManager = context.getSystemPersistenceManager();
        this.identifiers = new ArrayList<>();
        if(identifiers != null)
        {
            Iterator it = identifiers.iterator();
            while (it.hasNext())
                this.identifiers.add((RelationshipReference)it.next());
        }
        this.entityDescriptor = entityDescriptor;
        this.context = context;
        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    /**
     * Constructor
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param entityDescriptor Record Entity Descriptor
     * @param identifiers  List of References
     * @param context Schema Context
     */
    public LazyRelationshipCollection(EntityDescriptor entityDescriptor, List identifiers, SchemaContext context)
    {
        this.persistenceManager = context.getSystemPersistenceManager();
        this.identifiers = identifiers;
        this.entityDescriptor = entityDescriptor;
        this.context = context;
        this.partitionContext = new PartitionContext(context, entityDescriptor);
    }

    /**
     * Constructor
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param context Schema Context
     */
    public LazyRelationshipCollection(SchemaContext context)
    {
        this.persistenceManager = context.getSystemPersistenceManager();
        this.context = context;
    }

    /**
     * Size of record references
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @return Size of References
     */
    @Override
    public int size()
    {
        return identifiers.size();
    }

    /**
     * Collection is Empty
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @return Flag for indicating Collection is empty ( longSize == 0 )
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
     * @param o Record to check
     * @return Flag indicating record exists within collection
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
        try
        {
            return identifiers.contains(new RelationshipReference(identifier, partitionContext.getPartitionId((IManagedEntity) o)));
        }
        catch (EntityException e)
        {
            return false;
        }
    }

    /**
     * Add an element to the lazy collection
     * This must add a managed entity
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param e Record Managed Entity
     * @return Flag indicating added or not
     */
    @Override
    public boolean add(E e)
    {
        try
        {
            Object identifier = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) e, entityDescriptor.getIdentifier());

            IManagedEntity entity = persistenceManager.findByIdWithPartitionId(e.getClass(), identifier, partitionContext.getPartitionId((IManagedEntity) e));

            values.put(identifier, entity);
            identifiers.add(new RelationshipReference(identifier, partitionContext.getPartitionId(entity)));
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
     * @param index Record index
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
                RelationshipReference ref = identifiers.get(index);

                if(ref.partitionId > 0)
                {
                    entity = persistenceManager.findByIdWithPartitionId(entityDescriptor.getClazz(), ref.identifier, ref.partitionId);
                }
                else
                {
                    entity = persistenceManager.findById(entityDescriptor.getClazz(), ref.identifier);
                }
            } catch (EntityException e)
            {
                return null;
            }
            values.put(index, entity);
        }
        return (E) entity;
    }

    /**
     * Set object at index
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element Record
     * @return Record set at index
     */
    @Override
    public E set(int index, E element)
    {
        try
        {
            Object identifier = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) element, entityDescriptor.getIdentifier());

            Long partitionId = partitionContext.getPartitionId((IManagedEntity) element);
            IManagedEntity entity = persistenceManager.findByIdWithPartitionId(entityDescriptor.getClazz(), identifier, partitionId);


            Object existingObject = identifiers.get(index);
            values.remove(existingObject);
            values.put(identifier, entity);
            identifiers.set(index, new RelationshipReference(identifier, partitionId));
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
     * @param element Record
     */
    @Override
    public void add(int index, E element)
    {
        this.set(index, element);
    }

    /**
     * Remove object at index
     *
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param index Record Index
     * @return Record removed
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
     * @author Tim Osborn
     * @since 1.0.0
     *
     * @param o Record
     * @return If record was removed
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
        try
        {
            RelationshipReference ref = new RelationshipReference(identifier, partitionContext.getPartitionId((IManagedEntity) o));
            values.remove(ref);
            return identifiers.remove(ref);
        }
        catch (EntityException e)
        {
            return false;
        }
    }

    public List<RelationshipReference> getIdentifiers()
    {
        return identifiers;
    }

    public void setIdentifiers(List<RelationshipReference> identifiers)
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
    public void writeExternal(ObjectOutput out) throws IOException
    {
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
