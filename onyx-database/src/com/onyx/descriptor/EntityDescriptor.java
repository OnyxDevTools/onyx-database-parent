package com.onyx.descriptor;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemIndex;
import com.onyx.entity.SystemRelationship;
import com.onyx.exception.*;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.util.CompareUtil;
import com.onyx.util.EntityClassLoader;
import com.onyx.util.ReflectionUtil;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;


/**
 Created by timothy.osborn on 12/11/14.
 */
public class EntityDescriptor implements Serializable
{
    protected Class clazz;

    protected Entity entity;

    protected IdentifierDescriptor identifier;
    protected PartitionDescriptor partition;

    protected Method preUpdateCallback;
    protected Method preInsertCallback;
    protected Method preRemoveCallback;
    protected Method prePersistCallback;
    protected Method postUpdateCallback;
    protected Method postInsertCallback;
    protected Method postRemoveCallback;
    protected Method postPersistCallback;

    protected Map<String, AttributeDescriptor> attributes = new TreeMap();
    protected Map<String, IndexDescriptor> indexes = new TreeMap();
    protected Map<String, RelationshipDescriptor> relationships = new TreeMap();

    /**
     * Constructor - Initializes the entity descriptor with the target class.
     *
     * @param   clazz
     * @param   context
     *
     * @throws  EntityClassNotFoundException
     * @throws  AttributeMissingException
     * @throws  EntityException
     */
    public EntityDescriptor(final Class clazz, final SchemaContext context) throws EntityException
    {
        final boolean interfaceFound = IManagedEntity.class.isAssignableFrom(clazz); // clazz.isAssignableFrom(IManagedEntity.class);

        if (!interfaceFound)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND, clazz);
        }

        final boolean implementationFound = ManagedEntity.class.isAssignableFrom(clazz); // clazz.isAssignableFrom(IManagedEntity.class);

        if (!implementationFound)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.EXTENSION_NOT_FOUND, clazz);
        }

        // This class does not have the entity annotation.  This is required
        if (clazz.getAnnotation(Entity.class) == null)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND, clazz);
        }

        this.clazz = clazz;
        this.entity = (Entity) clazz.getAnnotation(Entity.class);

        final Set<Field> fields = new HashSet<Field>();

        Class tmpClass = clazz;

        while (tmpClass != Object.class)
        {
            fields.addAll(Arrays.asList(tmpClass.getDeclaredFields()));
            tmpClass = tmpClass.getSuperclass();
        }

        initEntityCallbacks(clazz);

        // Iterate through fields and get annotations
        for (final Field field : fields)
        {

            if (field.getAnnotation(Identifier.class) != null)
            {

                // Build id descriptor
                final Identifier annotation = field.getAnnotation(Identifier.class);
                this.identifier = new IdentifierDescriptor();
                identifier.setName(field.getName());
                identifier.setGenerator(annotation.generator());
                identifier.setType(field.getType());
                identifier.setLoadFactor((byte)annotation.loadFactor());
            }

            if (field.getAnnotation(Attribute.class) != null)
            {

                // Build Attribute descriptor
                final Attribute annotation = field.getAnnotation(Attribute.class);
                final AttributeDescriptor attribute = new AttributeDescriptor();
                attribute.setName(field.getName());
                attribute.setType(field.getType());
                attribute.setNullable(annotation.nullable());
                attribute.setSize(annotation.size());

                if (!field.isAccessible())
                {
                    field.setAccessible(true);
                }

                attribute.setField(field);

                attributes.put(field.getName(), attribute);
            }

            if (field.getAnnotation(Relationship.class) != null)
            {

                // Build relationship descriptor
                final Relationship annotation = field.getAnnotation(Relationship.class);
                final RelationshipDescriptor relationship = new RelationshipDescriptor();
                relationship.setName(field.getName());
                relationship.setType(field.getType());
                relationship.setInverseClass(annotation.inverseClass());
                relationship.setParentClass(clazz);
                relationship.setInverse(annotation.inverse());
                relationship.setCascadePolicy(annotation.cascadePolicy());
                relationship.setFetchPolicy(annotation.fetchPolicy());
                relationship.setRelationshipType(annotation.type());
                relationship.setEntityDescriptor(this);
                relationship.setLoadFactor((byte)annotation.loadFactor());
                relationships.put(field.getName(), relationship);
            }

            if (field.getAnnotation(Index.class) != null)
            {

                final Index annotation = field.getAnnotation(Index.class);

                // Build Index descriptor
                final IndexDescriptor index = new IndexDescriptor();
                index.setName(field.getName());
                index.setLoadFactor(annotation.loadFactor());
                index.setType(field.getType());
                index.setEntityDescriptor(this);

                indexes.put(field.getName(), index);
            }

            if (field.getAnnotation(Partition.class) != null)
            {
                this.partition = new PartitionDescriptor();
                this.partition.setName(field.getName());
                this.partition.setType(field.getType());

                this.partition.setPartitionField(ReflectionUtil.getOffsetField(field));
            }
        }

        // Validate Entity
        validateIdentifier();
        validateAttributes();
        validateRelationships();
        validateIndexes();

        if ((context != null) && (context.getLocation() != null) && (context.getClass() == DefaultSchemaContext.class))
        {
            EntityClassLoader.writeClass(this, context.getLocation());
        }

    }

    /**
     * Get entity callbacks from the annotations on the entity.
     *
     * @param  clazz  class that has callbacks
     */
    protected void initEntityCallbacks(final Class clazz)
    {
        Class tmpClass = clazz;

        while (tmpClass != Object.class)
        {
            final Method[] methods = tmpClass.getDeclaredMethods();

            for (final Method method : methods)
            {

                // get @PreInsert
                if ((this.preInsertCallback == null) && method.isAnnotationPresent(PreInsert.class))
                {
                    this.preInsertCallback = method;

                    if (!this.preInsertCallback.isAccessible())
                    {
                        this.preInsertCallback.setAccessible(true);
                    }
                }

                // get @PreUpdate
                if ((this.preUpdateCallback == null) && method.isAnnotationPresent(PreUpdate.class))
                {
                    this.preUpdateCallback = method;

                    if (!this.preUpdateCallback.isAccessible())
                    {
                        this.preUpdateCallback.setAccessible(true);
                    }
                }

                // get @PreRemove
                if ((this.preRemoveCallback == null) && method.isAnnotationPresent(PreRemove.class))
                {
                    this.preRemoveCallback = method;

                    if (!this.preRemoveCallback.isAccessible())
                    {
                        this.preRemoveCallback.setAccessible(true);
                    }
                }

                // get @PrePersist
                if ((this.prePersistCallback == null) && method.isAnnotationPresent(PrePersist.class))
                {
                    this.prePersistCallback = method;

                    if (!this.prePersistCallback.isAccessible())
                    {
                        this.prePersistCallback.setAccessible(true);
                    }
                }

                // get @PostInsert
                if ((this.postInsertCallback == null) && method.isAnnotationPresent(PostInsert.class))
                {
                    this.postInsertCallback = method;

                    if (!this.postInsertCallback.isAccessible())
                    {
                        this.postInsertCallback.setAccessible(true);
                    }
                }

                // get @PostUpdate
                if ((this.postUpdateCallback == null) && method.isAnnotationPresent(PostUpdate.class))
                {
                    this.postUpdateCallback = method;

                    if (!this.postUpdateCallback.isAccessible())
                    {
                        this.postUpdateCallback.setAccessible(true);
                    }
                }

                // get @PostRemove
                if ((this.postRemoveCallback == null) && method.isAnnotationPresent(PostRemove.class))
                {
                    this.postRemoveCallback = method;

                    if (!this.postRemoveCallback.isAccessible())
                    {
                        this.postRemoveCallback.setAccessible(true);
                    }
                }

                // get @PostPersist
                if ((this.postPersistCallback == null) && method.isAnnotationPresent(PostPersist.class))
                {
                    this.postPersistCallback = method;

                    if (!this.postPersistCallback.isAccessible())
                    {
                        this.postPersistCallback.setAccessible(true);
                    }
                }

            }

            tmpClass = tmpClass.getSuperclass();
        }

    }

    /**
     * Validate Relationships.
     *
     * @throws  EntityClassNotFoundException      when class is not found
     * @throws  InvalidRelationshipTypeException  when relationship is not valid
     */
    protected void validateRelationships() throws EntityClassNotFoundException, InvalidRelationshipTypeException
    {
        for (final RelationshipDescriptor descriptor : this.relationships.values())
        {
            final boolean interfaceFound = IManagedEntity.class.isAssignableFrom(clazz); // clazz.isAssignableFrom(IManagedEntity.class);

            if (!interfaceFound)
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_PERSISTED_NOT_FOUND + ": " +
                    descriptor.inverseClass.getName(), clazz);
            }

            final boolean extensionFound = ManagedEntity.class.isAssignableFrom(clazz); // clazz.isAssignableFrom(IManagedEntity.class);

            if (!extensionFound)
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_BASE_NOT_FOUND + ": " +
                        descriptor.inverseClass.getName(), clazz);
            }

            if ((descriptor.getType() != descriptor.getInverseClass()) &&
                    ((descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE) ||
                        (descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE)))
            {
                throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH);
            }

            if ((descriptor.inverseClass.getAnnotation(Entity.class) == null) &&
                    ((descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE) ||
                        (descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE)))
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_NOT_FOUND + ": " +
                    descriptor.inverseClass.getName(), clazz);
            }

            if ((descriptor.inverse != null) && (descriptor.inverse.length() > 0) &&
                    ((descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE) ||
                        (descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE)))
            {

                try
                {
                    final Field inverseField = descriptor.inverseClass.getDeclaredField(descriptor.inverse);

                    if ((descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE) && (inverseField.getType() != List.class))
                    {
                        throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH);
                    }
                    else if ((descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE) &&
                            (inverseField.getType() != descriptor.getParentClass()))
                    {
                        throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH);
                    }
                }
                catch (NoSuchFieldException e)
                {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID + " on " +
                        descriptor.getInverseClass(), e);
                }
            }

            if ((descriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY) ||
                    (descriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY))
            {

                try
                {
                    final Field attributeField = this.clazz.getDeclaredField(descriptor.getName());
                    boolean listFound = false;

                    if (attributeField.getType() == List.class)
                    {
                        listFound = true;
                    }

                    for (final Class inter : attributeField.getType().getInterfaces())
                    {

                        if (inter == List.class)
                        {
                            listFound = true;
                        }
                    }

                    if (!listFound)
                    {
                        throw new InvalidRelationshipTypeException(EntityClassNotFoundException.TO_MANY_INVALID_TYPE);
                    }
                }
                catch (NoSuchFieldException e)
                {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID, e);
                }
            }
        }
    }

    /**
     * Validate Indexes.
     *
     * @throws  InvalidIndexException
     */
    protected void validateIndexes() throws InvalidIndexException
    {
        for (final IndexDescriptor index : this.indexes.values())
        {

            try
            {
                final Field field = this.clazz.getDeclaredField(index.getName());

                if (field.getAnnotation(Attribute.class) == null)
                {
                    throw new InvalidIndexException(InvalidIndexException.INDEX_MISSING_ATTRIBUTE + ": " + index.getName());
                }
            }
            catch (NoSuchFieldException e)
            {
                throw new InvalidIndexException(InvalidIndexException.INDEX_MISSING_FIELD);
            }
        }
    }

    /**
     * Validate Attributes.
     *
     * @throws  EntityTypeMatchException
     */
    protected void validateAttributes() throws EntityTypeMatchException
    {
        for (final AttributeDescriptor attribute : this.attributes.values())
        {

            Class type = attribute.getType();

            if(Long.class.isAssignableFrom(type) ||
                Integer.class.isAssignableFrom(type) ||
                String.class.isAssignableFrom(type) ||
                Double.class.isAssignableFrom(type) ||
                Float.class.isAssignableFrom(type) ||
                Boolean.class.isAssignableFrom(type) ||
                Byte.class.isAssignableFrom(type) ||
                Date.class.isAssignableFrom(type) ||
                Short.class.isAssignableFrom(type) ||
                Character.class.isAssignableFrom(type) ||
                short.class.isAssignableFrom(type) ||
                long.class.isAssignableFrom(type) ||
                int.class.isAssignableFrom(type) ||
                double.class.isAssignableFrom(type) ||
                float.class.isAssignableFrom(type) ||
                boolean.class.isAssignableFrom(type) ||
                byte.class.isAssignableFrom(type) ||
                char.class.isAssignableFrom(type) ||
                byte[].class.isAssignableFrom(type) ||
                int[].class.isAssignableFrom(type) ||
                long[].class.isAssignableFrom(type) ||
                float[].class.isAssignableFrom(type) ||
                double[].class.isAssignableFrom(type) ||
                boolean[].class.isAssignableFrom(type) ||
                char[].class.isAssignableFrom(type) ||
                short[].class.isAssignableFrom(type) ||
                Character[].class.isAssignableFrom(type) ||
                Short[].class.isAssignableFrom(type) ||
                Byte[].class.isAssignableFrom(type) ||
                Integer[].class.isAssignableFrom(type) ||
                Long[].class.isAssignableFrom(type) ||
                Float[].class.isAssignableFrom(type) ||
                Double[].class.isAssignableFrom(type) ||
                String[].class.isAssignableFrom(type) ||
                Boolean[].class.isAssignableFrom(type) ||
                IManagedEntity.class.isAssignableFrom(type) ||
                List.class.isAssignableFrom(type) ||
                Set.class.isAssignableFrom(type) ||
                type.isEnum())
            {
                continue;
            }

            throw new EntityTypeMatchException(EntityTypeMatchException.ATTRIBUTE_TYPE_IS_NOT_SUPPORTED + ": " +
                attribute.getClass().getName());
        }
    }

    /**
     * Validate Identifier.
     *
     * @throws  InvalidIdentifierException
     */
    protected void validateIdentifier() throws InvalidIdentifierException
    {
        if (this.identifier == null)
        {
            throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING);
        }

        try
        {
            final Field idField = clazz.getDeclaredField(this.identifier.getName());

            if (idField.getAnnotation(Attribute.class) == null)
            {
                throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING_ATTRIBUTE);
            }

            if (!idField.getType().getName().equals(Date.class.getName()) &&
                    !idField.getType().getName().equals(Long.class.getName()) &&
                    !idField.getType().getName().equals(long.class.getName()) &&
                    !idField.getType().getName().equals(Integer.class.getName()) &&
                    !idField.getType().getName().equals(int.class.getName()) &&
                    !idField.getType().getName().equals(String.class.getName()))
            {
                throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_TYPE);
            }

            if (((idField.getType() != Integer.class) && (idField.getType() != int.class) && (idField.getType() != Long.class) &&
                        (idField.getType() != long.class)) && (identifier.getGenerator() == IdentifierGenerator.SEQUENCE))
            {
                throw new InvalidIdentifierException((InvalidIdentifierException.INVALID_GENERATOR));
            }
        }
        catch (NoSuchFieldException e)
        {
            throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING_ATTRIBUTE);
        }
    }

    ////////////////////////////////////////////////////////
    //
    // Hashing Object Overrides
    //
    ////////////////////////////////////////////////////////
    /**
     * Used for calculating hash structure.
     *
     * @return  used for calculating hash structure.
     */
    @Override public int hashCode()
    {
        if (this.getPartition() == null)
        {
            return clazz.getName().hashCode();
        }

        return (clazz.getName() + this.getPartition().getPartitionValue()).hashCode();
    }

    /**
     * Check For Index Changes.
     *
     * @param  systemEntity          System Entity serial version
     * @param  rebuildIndexConsumer  consumer used to re-build index
     */
    public void checkIndexChanges(final SystemEntity systemEntity, final Consumer<IndexDescriptor> rebuildIndexConsumer)
    {
        IndexDescriptor indexDescriptor = null;
        SystemIndex systemIndex = null;

        final Map<String, SystemIndex> indexMap = new HashMap();

        for (int i = 0; i < systemEntity.getIndexes().size(); i++)
        {
            systemIndex = systemEntity.getIndexes().get(i);
            indexMap.put(systemIndex.getName(), systemIndex);
        }

        final Iterator<IndexDescriptor> indexIterator = this.getIndexes().values().iterator();

        while (indexIterator.hasNext())
        {
            indexDescriptor = indexIterator.next();
            systemIndex = indexMap.get(indexDescriptor.getName());

            if (systemIndex == null)
            {
                rebuildIndexConsumer.accept(indexDescriptor);
            }
            else if ((systemIndex != null) && !indexDescriptor.getType().getName().equals(systemIndex.getType()))
            {
                rebuildIndexConsumer.accept(indexDescriptor);
            }
        }
    }

    /**
     * Check for valid relationships.
     *
     * @param   systemEntity
     *
     * @throws  InvalidRelationshipTypeException  when relationship is invalid
     */
    public void checkValidRelationships(final SystemEntity systemEntity) throws InvalidRelationshipTypeException
    {
        // Build Relationship Map
        final Map<String, SystemRelationship> relationshipMap = new HashMap();
        SystemRelationship systemRelationship = null;

        for (int i = 0; i < systemEntity.getRelationships().size(); i++)
        {
            systemRelationship = systemEntity.getRelationships().get(i);
            relationshipMap.put(systemRelationship.getName(), systemRelationship);
        }

        // Iterate through and check for changes
        final Iterator<RelationshipDescriptor> relationshipDescriptorIterator = this.getRelationships().values().iterator();
        RelationshipDescriptor relationshipDescriptor = null;

        while (relationshipDescriptorIterator.hasNext())
        {
            relationshipDescriptor = relationshipDescriptorIterator.next();
            systemRelationship = relationshipMap.get(relationshipDescriptor.getName());

            if (systemRelationship != null && (systemRelationship.getRelationshipType() == RelationshipType.MANY_TO_MANY.ordinal()) ||
                    (systemRelationship != null && systemRelationship.getRelationshipType() == RelationshipType.ONE_TO_MANY.ordinal()))
            {

                if ((relationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE) ||
                        (relationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE))
                {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.CANNOT_UPDATE_RELATIONSHIP);
                }
            }

        }
    }

    /**
     * Used for usage within a hashmap.
     *
     * @param   val
     *
     * @return  used for usage within a hashmap.
     */
    @Override public boolean equals(final Object val)
    {
        if (val instanceof SystemEntity)
        {

            // Compare Attributes
            final Iterator<Map.Entry<String, AttributeDescriptor>> it = this.attributes.entrySet().iterator();
            Map.Entry<String, AttributeDescriptor> attributeEntry = null;

            final SystemEntity systemEntity = (SystemEntity) val;

            for (int i = 0; i < systemEntity.getAttributes().size(); i++)
            {
                final SystemAttribute systemAttribute = systemEntity.getAttributes().get(i);

                if (!this.attributes.containsKey(systemAttribute.getName()))
                {
                    return false;
                }
                else if (!this.attributes.get(systemAttribute.getName()).getType().getName().equals(systemAttribute.getDataType()))
                {
                    return false;
                }
            }

            while (it.hasNext())
            {
                attributeEntry = it.next();

                boolean found = false;

                for (int i = 0; i < systemEntity.getAttributes().size(); i++)
                {
                    final SystemAttribute systemAttribute = systemEntity.getAttributes().get(i);

                    if (systemAttribute.getName().equals(attributeEntry.getKey()))
                    {

                        if (!attributeEntry.getValue().getType().getName().equals(systemAttribute.getDataType()))
                        {
                            return false;
                        }

                        found = true;

                        break;
                    }
                }

                if (!found)
                {
                    return false;
                }

            }

            // Compare Relationships
            final Iterator<Map.Entry<String, RelationshipDescriptor>> rltnIterator = this.relationships.entrySet().iterator();
            Map.Entry<String, RelationshipDescriptor> relationshipEntry = null;

            for (int i = 0; i < systemEntity.getRelationships().size(); i++)
            {
                final SystemRelationship systemRelationship = systemEntity.getRelationships().get(i);

                if (!this.relationships.containsKey(systemRelationship.getName()))
                {
                    return false;
                }
                else if (
                    !this.relationships.get(systemRelationship.getName()).getInverseClass().getName().equals(
                            systemRelationship.getInverseClass()))
                {
                    return false;
                }
                else if (
                    !this.relationships.get(systemRelationship.getName()).getParentClass().getName().equals(
                            systemRelationship.getParentClass()))
                {
                    return false;
                }
            }

            while (rltnIterator.hasNext())
            {
                relationshipEntry = rltnIterator.next();

                boolean found = false;

                for (int i = 0; i < systemEntity.getRelationships().size(); i++)
                {
                    final SystemRelationship systemRelationship = systemEntity.getRelationships().get(i);

                    if (systemRelationship.getName().equals(relationshipEntry.getKey()))
                    {

                        if (!relationshipEntry.getValue().getInverseClass().getName().equals(systemRelationship.getInverseClass()))
                        {
                            return false;
                        }
                        else if (
                            !relationshipEntry.getValue().getParentClass().getName().equals(systemRelationship.getParentClass()))
                        {
                            return false;
                        }

                        found = true;

                        break;
                    }
                }

                if (!found)
                {
                    return false;
                }

            }

            return true;
        }

        if (val instanceof EntityDescriptor)
        {
            final EntityDescriptor compare = (EntityDescriptor) val;

            if ((compare.getPartition() == null) && (this.getPartition() == null))
            {
                return (((EntityDescriptor) val).getClazz().getName().equals(getClazz().getName()));
            }
            else if (compare.getPartition() == null)
            {
                return false;
            }
            else if (this.getPartition() == null)
            {
                return false;
            }
            else
            {

                try
                {
                    return CompareUtil.compare(compare.getClazz().getName(), this.getClazz().getName(),
                            QueryCriteriaOperator.EQUAL) &&
                        CompareUtil.compare(compare.getPartition().getPartitionValue(), this.getPartition().getPartitionValue(),
                            QueryCriteriaOperator.EQUAL);
                }
                catch (InvalidDataTypeForOperator invalidDataTypeForOperator)
                {
                }
            }
        }

        return false;
    }

    ////////////////////////////////////////////////////////
    //
    // Getters and Setters
    //
    ////////////////////////////////////////////////////////
    public Class getClazz()
    {
        return clazz;
    }

    public Entity getEntity()
    {
        return entity;
    }

    public void setEntity(final Entity entity)
    {
        this.entity = entity;
    }

    public IdentifierDescriptor getIdentifier()
    {
        return identifier;
    }

    public void setIdentifier(final IdentifierDescriptor identifier)
    {
        this.identifier = identifier;
    }

    public PartitionDescriptor getPartition()
    {
        return partition;
    }

    public void setPartition(final PartitionDescriptor partition)
    {
        this.partition = partition;
    }

    public Method getPreUpdateCallback()
    {
        return preUpdateCallback;
    }

    public void setPreUpdateCallback(final Method preUpdateCallback)
    {
        this.preUpdateCallback = preUpdateCallback;
    }

    public Method getPreInsertCallback()
    {
        return preInsertCallback;
    }

    public void setPreInsertCallback(final Method preInsertCallback)
    {
        this.preInsertCallback = preInsertCallback;
    }

    public Method getPreRemoveCallback()
    {
        return preRemoveCallback;
    }

    public void setPreRemoveCallback(final Method preRemoveCallback)
    {
        this.preRemoveCallback = preRemoveCallback;
    }

    public Method getPrePersistCallback()
    {
        return prePersistCallback;
    }

    public void setPrePersistCallback(final Method prePersistCallback)
    {
        this.prePersistCallback = prePersistCallback;
    }

    public Method getPostUpdateCallback()
    {
        return postUpdateCallback;
    }

    public void setPostUpdateCallback(final Method postUpdateCallback)
    {
        this.postUpdateCallback = postUpdateCallback;
    }

    public Method getPostInsertCallback()
    {
        return postInsertCallback;
    }

    public void setPostInsertCallback(final Method postInsertCallback)
    {
        this.postInsertCallback = postInsertCallback;
    }

    public Method getPostRemoveCallback()
    {
        return postRemoveCallback;
    }

    public void setPostRemoveCallback(final Method postRemoveCallback)
    {
        this.postRemoveCallback = postRemoveCallback;
    }

    public Method getPostPersistCallback()
    {
        return postPersistCallback;
    }

    public void setPostPersistCallback(final Method postPersistCallback)
    {
        this.postPersistCallback = postPersistCallback;
    }

    public Map<String, AttributeDescriptor> getAttributes()
    {
        return attributes;
    }

    public void setAttributes(final Map<String, AttributeDescriptor> attributes)
    {
        this.attributes = attributes;
    }

    public Map<String, IndexDescriptor> getIndexes()
    {
        return indexes;
    }

    public void setIndexes(final Map<String, IndexDescriptor> indexes)
    {
        this.indexes = indexes;
    }

    public Map<String, RelationshipDescriptor> getRelationships()
    {
        return relationships;
    }

    public void setRelationships(final Map<String, RelationshipDescriptor> relationships)
    {
        this.relationships = relationships;
    }

    /**
     * Get file name for data storage.
     *
     * @return  get file name for data storage.
     */
    public String getFileName()
    {
        if (entity.fileName().equals(""))
        {
            return "data.dat";
        }

        return entity.fileName();
    }

    /**
     * Create new entity with the type specified in the descriptor.
     *
     * @param   clazz
     *
     * @return  create new entity with the type specified in the descriptor.
     *
     * @throws  InvalidConstructorException
     */
    public static IManagedEntity createNewEntity(final Class clazz) throws InvalidConstructorException
    {
        final IManagedEntity e;

        if (clazz == null)
        {
            throw new InvalidConstructorException(InvalidConstructorException.MISSING_ENTITY_TYPE, null);
        }

        try
        {
            e = (IManagedEntity) clazz.newInstance();
        }
        catch (InstantiationException e1)
        {
            throw new InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e1);
        }
        catch (IllegalAccessException e1)
        {
            throw new InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e1);
        }

        return e;
    }

}
