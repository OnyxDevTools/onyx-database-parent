package com.onyx.persistence.collections;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.BufferingException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.record.AbstractRecordController;
import com.onyx.relationship.RelationshipReference;

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
public class LazyRelationshipCollection<E> extends ArrayList<E> implements List<E>, BufferStreamable {

    protected List<RelationshipReference> identifiers = null;
    transient protected EntityDescriptor entityDescriptor = null;

    transient protected Map<Object, IManagedEntity> values = new WeakHashMap<>();
    transient protected PersistenceManager persistenceManager;
    private String contextId;

    @SuppressWarnings("unused")
    public LazyRelationshipCollection()
    {

    }

    /**
     * Constructor
     *
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
            for (Object identifier : identifiers) this.identifiers.add((RelationshipReference) identifier);
        }
        this.entityDescriptor = entityDescriptor;
        this.contextId = context.getContextId();
    }

    /**
     * Size of record references
     *
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
     * @since 1.0.0
     *
     * @param o Record to check
     * @return Flag indicating record exists within collection
     */
    @Override
    public boolean contains(Object o)
    {
        try
        {
            AbstractRecordController.getIndexValueFromEntity((IManagedEntity) o, entityDescriptor.getIdentifier());
            return true;
        } catch (EntityException e)
        {
            return false;
        }
    }

    /**
     * Add an element to the lazy collection
     * This must add a managed entity
     *
     * @since 1.0.0
     *
     * @param e Record Managed Entity
     * @return Flag indicating added or not
     */
    @Override
    public boolean add(E e)
    {
        throw new RuntimeException("Method unsupported, hydrate relationship using initialize before modifying");
    }

    /**
     * Remove all objects
     *
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
     * @since 1.0.0
     *
     * @param index Record index
     * @return ManagedEntity
     */
    @Override
    @SuppressWarnings("unchecked")
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
     * @since 1.0.0
     *
     * @param index Record Index
     * @param element Record
     * @return Record set at index
     */
    @Override
    public E set(int index, E element)
    {
        throw new RuntimeException("Method unsupported, hydrate relationship using initialize before modifying");
    }

    /**
     * Add object at index
     *
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
     * @since 1.0.0
     *
     * @param index Record Index
     * @return Record removed
     */
    @Override
    @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
    public E remove(int index)
    {
        Object existingObject = identifiers.get(index);
        identifiers.remove(existingObject);
        return (E) values.remove(existingObject);
    }

    /**
     * Remove object at index
     *
     * @since 1.0.0
     *
     * @param o Record
     * @return If record was removed
     */
    @Override
    public boolean remove(Object o)
    {
        throw new RuntimeException("Method unsupported, hydrate relationship using initialize before modifying");
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
    }

    @Override
    @SuppressWarnings("unchecked")
    public void read(BufferStream bufferStream) throws BufferingException {
        this.values = new WeakHashMap<>();
        this.identifiers = (List) bufferStream.getCollection();
        String className = bufferStream.getString();
        this.contextId = bufferStream.getString();

        SchemaContext context = DefaultSchemaContext.registeredSchemaContexts.get(contextId);
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
        bufferStream.putString(this.contextId);
    }
}
