package com.onyx.descriptor;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemIndex;
import com.onyx.entity.SystemRelationship;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.annotations.*;
import com.onyx.exception.*;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.util.AttributeField;
import com.onyx.util.EntityClassLoader;
import com.onyx.util.CompareUtil;
import gnu.trove.THashMap;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * Created by timothy.osborn on 12/11/14.
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
     * Constructor - Initializes the entity descriptor with the target class
     *
     * @param clazz
     * @throws EntityClassNotFoundException
     * @throws AttributeMissingException
     */
    public EntityDescriptor(Class clazz, SchemaContext context) throws EntityException
    {

        boolean interfaceFound = IManagedEntity.class.isAssignableFrom(clazz);//clazz.isAssignableFrom(IManagedEntity.class);

        if(!interfaceFound)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND);
        }

        // This class does not have the entity annotation.  This is required
        if (clazz.getAnnotation(Entity.class) == null)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND);
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
        for (Field field : fields)
        {
            if (field.getAnnotation(Identifier.class) != null)
            {
                // Build id descriptor
                Identifier annotation = field.getAnnotation(Identifier.class);
                this.identifier = new IdentifierDescriptor();
                identifier.setName(field.getName());
                identifier.setGenerator(annotation.generator());
                identifier.setType(field.getType());
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

                if(!field.isAccessible())
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
                relationships.put(field.getName(), relationship);
            }

            if (field.getAnnotation(Index.class) != null)
            {
                // Build Index descriptor
                final IndexDescriptor index = new IndexDescriptor();
                index.setName(field.getName());
                index.setType(field.getType());
                index.setEntityDescriptor(this);

                indexes.put(field.getName(), index);
            }

            if(field.getAnnotation(Partition.class) != null)
            {
                this.partition = new PartitionDescriptor();
                this.partition.setName(field.getName());
                this.partition.setType(field.getType());
                this.partition.setPartitionField(new AttributeField(field));
            }
        }

        // Validate Entity
        validateIdentifier();
        validateAttributes();
        validateRelationships();
        validateIndexes();

        if(context != null && context.getLocation() != null  && context.getClass() == DefaultSchemaContext.class)
            EntityClassLoader.writeClass(this, context.getLocation());

    }

    /**
     * Get entity callbacks from the annotations on the entity
     *
     * @param clazz
     */
    protected void initEntityCallbacks(Class clazz)
    {
        Class tmpClass = clazz;
        while (tmpClass != Object.class)
        {
            Method[] methods = tmpClass.getDeclaredMethods();
            for(Method method : methods){
                //get @PreInsert
                if(this.preInsertCallback == null && method.isAnnotationPresent(PreInsert.class)) {
                    this.preInsertCallback = method;
                    if(!this.preInsertCallback.isAccessible())
                    {
                        this.preInsertCallback.setAccessible(true);
                    }
                }
                //get @PreUpdate
                if(this.preUpdateCallback == null && method.isAnnotationPresent(PreUpdate.class)) {
                    this.preUpdateCallback = method;
                    if(!this.preUpdateCallback.isAccessible())
                    {
                        this.preUpdateCallback.setAccessible(true);
                    }
                }
                //get @PreRemove
                if(this.preRemoveCallback == null && method.isAnnotationPresent(PreRemove.class)) {
                    this.preRemoveCallback = method;
                    if(!this.preRemoveCallback.isAccessible())
                    {
                        this.preRemoveCallback.setAccessible(true);
                    }
                }
                //get @PrePersist
                if(this.prePersistCallback == null && method.isAnnotationPresent(PrePersist.class)) {
                    this.prePersistCallback = method;
                    if(!this.prePersistCallback.isAccessible())
                    {
                        this.prePersistCallback.setAccessible(true);
                    }
                }
                //get @PostInsert
                if(this.postInsertCallback == null && method.isAnnotationPresent(PostInsert.class)) {
                    this.postInsertCallback = method;
                    if(!this.postInsertCallback.isAccessible())
                    {
                        this.postInsertCallback.setAccessible(true);
                    }
                }
                //get @PostUpdate
                if(this.postUpdateCallback == null && method.isAnnotationPresent(PostUpdate.class)) {
                    this.postUpdateCallback = method;
                    if(!this.postUpdateCallback.isAccessible())
                    {
                        this.postUpdateCallback.setAccessible(true);
                    }
                }
                //get @PostRemove
                if(this.postRemoveCallback == null && method.isAnnotationPresent(PostRemove.class)) {
                    this.postRemoveCallback = method;
                    if(!this.postRemoveCallback.isAccessible())
                    {
                        this.postRemoveCallback.setAccessible(true);
                    }
                }
                //get @PostPersist
                if(this.postPersistCallback == null && method.isAnnotationPresent(PostPersist.class)) {
                    this.postPersistCallback = method;
                    if(!this.postPersistCallback.isAccessible())
                    {
                        this.postPersistCallback.setAccessible(true);
                    }
                }

            }
            tmpClass = tmpClass.getSuperclass();
        }

    }

    /**
     * Validate Relationships
     *
     * @throws EntityClassNotFoundException
     * @throws InvalidRelationshipTypeException
     */
    protected void validateRelationships() throws EntityClassNotFoundException, InvalidRelationshipTypeException
    {

        for(RelationshipDescriptor descriptor : this.relationships.values())
        {
            boolean interfaceFound = false;
            for(Class inter : descriptor.inverseClass.getInterfaces())
            {
                if(inter == IManagedEntity.class)
                {
                    interfaceFound = true;
                }

                for(Class inner : inter.getInterfaces())
                {
                    if(inner == IManagedEntity.class)
                    {
                        interfaceFound = true;
                        break;
                    }
                }
            }
            if(!interfaceFound)
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_PERSISTED_NOT_FOUND + ": " + descriptor.inverseClass.getCanonicalName());
            }

            if(descriptor.getType() != descriptor.getInverseClass() && (descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE || descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE))
            {
                throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH);
            }

            if(descriptor.inverseClass.getAnnotation(Entity.class) == null && (descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE || descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE))
                throw new EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_NOT_FOUND + ": " + descriptor.inverseClass.getCanonicalName());


            if(descriptor.inverse != null && descriptor.inverse.length() > 0 && (descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE || descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE))
            {
                try
                {
                    Field inverseField = descriptor.inverseClass.getDeclaredField(descriptor.inverse);

                    if(descriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE && inverseField.getType() != List.class)
                    {
                        throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH);
                    }
                    else if(descriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE && inverseField.getType() != descriptor.getParentClass())
                    {
                        throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH);
                    }
                } catch (NoSuchFieldException e)
                {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID, e);
                }
            }

            if(descriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY || descriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY)
            {
                try
                {
                    Field attributeField = this.clazz.getDeclaredField(descriptor.getName());
                    boolean listFound = false;
                    if(attributeField.getType() == List.class)
                    {
                        listFound = true;
                    }

                    for(Class inter : attributeField.getType().getInterfaces())
                    {
                        if(inter == List.class)
                        {
                            listFound = true;
                        }
                    }
                    if(!listFound)
                    {
                        throw new InvalidRelationshipTypeException(EntityClassNotFoundException.TO_MANY_INVALID_TYPE);
                    }
                } catch (NoSuchFieldException e)
                {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID, e);
                }
            }
        }
    }

    /**
     * Validate Indexes
     *
     * @throws InvalidIndexException
     */
    protected void validateIndexes() throws InvalidIndexException
    {

        for(IndexDescriptor index : this.indexes.values())
        {

            try
            {
                final Field field = this.clazz.getDeclaredField(index.getName());
                if(field.getAnnotation(Attribute.class) == null)
                {
                    throw new InvalidIndexException(InvalidIndexException.INDEX_MISSING_ATTRIBUTE + ": " + index.getName());
                }
            } catch (NoSuchFieldException e)
            {
                throw new InvalidIndexException(InvalidIndexException.INDEX_MISSING_FIELD);
            }
        }
    }

    /**
     * Validate Attributes
     *
     * @throws EntityTypeMatchException
     */
    protected void validateAttributes() throws EntityTypeMatchException
    {
        for(AttributeDescriptor attribute : this.attributes.values())
        {
            if(!attribute.getType().getCanonicalName().equals(Date.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(Long.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(long.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(Integer.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(int.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(String.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(Double.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(double.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(boolean.class.getCanonicalName())
                    && !attribute.getType().getCanonicalName().equals(Boolean.class.getCanonicalName()))
            {
                throw new EntityTypeMatchException(EntityTypeMatchException.ATTRIBUTE_TYPE_IS_NOT_SUPPORTED + ": " + attribute.getClass().getCanonicalName());
            }
        }
    }

    /**
     * Validate Identifier
     *
     * @throws InvalidIdentifierException
     */
    protected void validateIdentifier() throws InvalidIdentifierException
    {
        if(this.identifier == null)
        {
            throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING);
        }

        try
        {
            Field idField = clazz.getDeclaredField(this.identifier.getName());
            if(idField.getAnnotation(Attribute.class) == null)
            {
                throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING_ATTRIBUTE);
            }
            if(!idField.getType().getCanonicalName().equals(Date.class.getCanonicalName())
                    && !idField.getType().getCanonicalName().equals(Long.class.getCanonicalName())
                    && !idField.getType().getCanonicalName().equals(long.class.getCanonicalName())
                    && !idField.getType().getCanonicalName().equals(Integer.class.getCanonicalName())
                    && !idField.getType().getCanonicalName().equals(int.class.getCanonicalName())
                    && !idField.getType().getCanonicalName().equals(String.class.getCanonicalName()))
            {
                throw  new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_TYPE);
            }

            if((idField.getType() != Integer.class
                    && idField.getType() != int.class
                    && idField.getType() != Long.class
                    && idField.getType() != long.class)
                    && identifier.getGenerator() == IdentifierGenerator.SEQUENCE)
            {
                throw new InvalidIdentifierException((InvalidIdentifierException.INVALID_GENERATOR));
            }
        } catch (NoSuchFieldException e)
        {
            throw new InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING_ATTRIBUTE);
        }
    }

    ////////////////////////////////////////////////////////
    //
    //  Hashing Object Overrides
    //
    ////////////////////////////////////////////////////////
    /**
     * Used for calculating hash map
     *
     * @return
     */
    @Override
    public int hashCode()
    {

        if(this.getPartition() == null)
        {
            return clazz.getCanonicalName().hashCode();
        }
        return (clazz.getCanonicalName() + this.getPartition().getPartitionValue()).hashCode();
    }

    /**
     * Check For Index Changes
     *
     * @param systemEntity System Entity serial version
     * @param rebuildIndexConsumer consumer used to re-build index
     * @return void nothing
     */
    public void checkIndexChanges(SystemEntity systemEntity, Consumer<IndexDescriptor> rebuildIndexConsumer)
    {
        IndexDescriptor indexDescriptor = null;
        SystemIndex systemIndex = null;

        Map<String, SystemIndex> indexMap = new THashMap();
        for(int i = 0; i < systemEntity.getIndexes().size(); i++)
        {
            systemIndex = systemEntity.getIndexes().get(i);
            indexMap.put(systemIndex.getName(), systemIndex);
        }

        Iterator<IndexDescriptor> indexIterator = this.getIndexes().values().iterator();

        while(indexIterator.hasNext())
        {
            indexDescriptor = indexIterator.next();
            systemIndex = indexMap.get(indexDescriptor.getName());
            if(systemIndex == null)
                rebuildIndexConsumer.accept(indexDescriptor);

            else if(systemIndex != null && !indexDescriptor.getType().getSimpleName().equals(systemIndex.getType()))
                rebuildIndexConsumer.accept(indexDescriptor);
        }
    }

    /**
     * Check for valid relationships
     *
     * @param systemEntity
     * @throws InvalidRelationshipTypeException
     */
    public void checkValidRelationships(SystemEntity systemEntity) throws InvalidRelationshipTypeException {

        // Build Relationship Map
        Map<String, SystemRelationship> relationshipMap = new THashMap();
        SystemRelationship systemRelationship = null;

        for (int i = 0; i < systemEntity.getRelationships().size(); i++) {
            systemRelationship = systemEntity.getRelationships().get(i);
            relationshipMap.put(systemRelationship.getName(), systemRelationship);
        }

        // Iterate through and check for changes
        Iterator<RelationshipDescriptor> relationshipDescriptorIterator = this.getRelationships().values().iterator();
        RelationshipDescriptor relationshipDescriptor = null;

        while (relationshipDescriptorIterator.hasNext()) {
            relationshipDescriptor = relationshipDescriptorIterator.next();
            systemRelationship = relationshipMap.get(relationshipDescriptor.getName());

            if (systemRelationship.getRelationshipType() == RelationshipType.MANY_TO_MANY.ordinal()
                    || systemRelationship.getRelationshipType() == RelationshipType.ONE_TO_MANY.ordinal()) {
                if (relationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_ONE ||
                        relationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_ONE) {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.CANNOT_UPDATE_RELATIONSHIP);
                }
            }

        }
    }

    /**
     * Used for usage within a hashmap
     * @param val
     * @return
     */
    @Override
    public boolean equals(Object val)
    {
        if(val instanceof SystemEntity)
        {
            // Compare Attributes
            Iterator<Map.Entry<String, AttributeDescriptor>> it = this.attributes.entrySet().iterator();
            Map.Entry<String, AttributeDescriptor> attributeEntry = null;

            SystemEntity systemEntity = (SystemEntity)val;
            for(int i = 0; i < systemEntity.getAttributes().size(); i++)
            {
                SystemAttribute systemAttribute = systemEntity.getAttributes().get(i);
                if(!this.attributes.containsKey(systemAttribute.getName()))
                    return false;
                else if(!this.attributes.get(systemAttribute.getName()).getType().getSimpleName().equals(systemAttribute.getDataType()))
                    return false;
            }

            while(it.hasNext())
            {
                attributeEntry = it.next();

                boolean found = false;
                for(int i = 0; i < systemEntity.getAttributes().size(); i++)
                {
                    SystemAttribute systemAttribute = systemEntity.getAttributes().get(i);

                    if(systemAttribute.getName().equals(attributeEntry.getKey()))
                    {
                        if(!attributeEntry.getValue().getType().getSimpleName().equals(systemAttribute.getDataType()))
                            return false;

                        found = true;
                        break;
                    }
                }

                if(!found)
                    return false;

            }

            // Compare Relationships
            Iterator<Map.Entry<String, RelationshipDescriptor>> rltnIterator = this.relationships.entrySet().iterator();
            Map.Entry<String, RelationshipDescriptor> relationshipEntry = null;

            for(int i = 0; i < systemEntity.getRelationships().size(); i++)
            {
                SystemRelationship systemRelationship = systemEntity.getRelationships().get(i);
                if(!this.relationships.containsKey(systemRelationship.getName()))
                    return false;
                else if(!this.relationships.get(systemRelationship.getName()).getInverseClass().getCanonicalName().equals(systemRelationship.getInverseClass()))
                    return false;
                else if(!this.relationships.get(systemRelationship.getName()).getParentClass().getCanonicalName().equals(systemRelationship.getParentClass()))
                    return false;
            }

            while(rltnIterator.hasNext())
            {
                relationshipEntry = rltnIterator.next();

                boolean found = false;
                for(int i = 0; i < systemEntity.getRelationships().size(); i++)
                {
                    SystemRelationship systemRelationship = systemEntity.getRelationships().get(i);
                    if(systemRelationship.getName().equals(relationshipEntry.getKey()))
                    {
                        if(!relationshipEntry.getValue().getInverseClass().getCanonicalName().equals(systemRelationship.getInverseClass()))
                            return false;
                        else if(!relationshipEntry.getValue().getParentClass().getCanonicalName().equals(systemRelationship.getParentClass()))
                            return false;

                        found = true;
                        break;
                    }
                }

                if(!found)
                    return false;

            }

            return true;
        }
        if(val instanceof EntityDescriptor)
        {
            final EntityDescriptor compare = (EntityDescriptor)val;
            if(compare.getPartition() == null && this.getPartition() == null)
            {
                return (((EntityDescriptor) val).getClazz().getCanonicalName().equals(getClazz().getCanonicalName()));
            }
            else if(compare.getPartition() == null)
            {
                return false;
            }
            else if(this.getPartition() == null)
            {
                return false;
            }
            else
            {
                try
                {
                    return CompareUtil.compare(compare.getClazz().getCanonicalName(), this.getClazz().getCanonicalName(), QueryCriteriaOperator.EQUAL)
                            && CompareUtil.compare(compare.getPartition().getPartitionValue(), this.getPartition().getPartitionValue(), QueryCriteriaOperator.EQUAL);
                } catch (InvalidDataTypeForOperator invalidDataTypeForOperator)
                { }
            }
        }

        return false;
    }

    ////////////////////////////////////////////////////////
    //
    //  Getters and Setters
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

    public void setEntity(Entity entity)
    {
        this.entity = entity;
    }

    public IdentifierDescriptor getIdentifier()
    {
        return identifier;
    }

    public void setIdentifier(IdentifierDescriptor identifier)
    {
        this.identifier = identifier;
    }

    public PartitionDescriptor getPartition()
    {
        return partition;
    }

    public void setPartition(PartitionDescriptor partition)
    {
        this.partition = partition;
    }

    public Method getPreUpdateCallback() {
        return preUpdateCallback;
    }

    public void setPreUpdateCallback(Method preUpdateCallback) {
        this.preUpdateCallback = preUpdateCallback;
    }

    public Method getPreInsertCallback()
    {
        return preInsertCallback;
    }

    public void setPreInsertCallback(Method preInsertCallback)
    {
        this.preInsertCallback = preInsertCallback;
    }

    public Method getPreRemoveCallback()
    {
        return preRemoveCallback;
    }

    public void setPreRemoveCallback(Method preRemoveCallback)
    {
        this.preRemoveCallback = preRemoveCallback;
    }

    public Method getPrePersistCallback()
    {
        return prePersistCallback;
    }

    public void setPrePersistCallback(Method prePersistCallback)
    {
        this.prePersistCallback = prePersistCallback;
    }

    public Method getPostUpdateCallback()
    {
        return postUpdateCallback;
    }

    public void setPostUpdateCallback(Method postUpdateCallback)
    {
        this.postUpdateCallback = postUpdateCallback;
    }

    public Method getPostInsertCallback()
    {
        return postInsertCallback;
    }

    public void setPostInsertCallback(Method postInsertCallback)
    {
        this.postInsertCallback = postInsertCallback;
    }

    public Method getPostRemoveCallback()
    {
        return postRemoveCallback;
    }

    public void setPostRemoveCallback(Method postRemoveCallback)
    {
        this.postRemoveCallback = postRemoveCallback;
    }

    public Method getPostPersistCallback()
    {
        return postPersistCallback;
    }

    public void setPostPersistCallback(Method postPersistCallback)
    {
        this.postPersistCallback = postPersistCallback;
    }

    public Map<String, AttributeDescriptor> getAttributes()
    {
        return attributes;
    }

    public void setAttributes(Map<String, AttributeDescriptor> attributes)
    {
        this.attributes = attributes;
    }

    public Map<String, IndexDescriptor> getIndexes()
    {
        return indexes;
    }

    public void setIndexes(Map<String, IndexDescriptor> indexes)
    {
        this.indexes = indexes;
    }

    public Map<String, RelationshipDescriptor> getRelationships()
    {
        return relationships;
    }

    public void setRelationships(Map<String, RelationshipDescriptor> relationships) {
        this.relationships = relationships;
    }

    /**
     * Get file name for data storage
     * @return
     */
    public String getFileName()
    {
        if(entity.fileName().equals(""))
        {
            return "data.dat";
        }
        return entity.fileName();
    }

    /**
     * Create new entity with the type specified in the descriptor
     *
     * @param clazz
     * @return
     * @throws InvalidConstructorException
     */
    public static IManagedEntity createNewEntity(Class clazz) throws InvalidConstructorException
    {
        final IManagedEntity e;
        
        if(clazz == null){
            throw new InvalidConstructorException(InvalidConstructorException.MISSING_ENTITY_TYPE, null);
        }
        
        try
        {
            e = (IManagedEntity)clazz.newInstance();
        } catch (InstantiationException e1)
        {
            throw new InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e1);
        } catch (IllegalAccessException e1)
        {
            throw new InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e1);
        }
        return e;
    }

}
