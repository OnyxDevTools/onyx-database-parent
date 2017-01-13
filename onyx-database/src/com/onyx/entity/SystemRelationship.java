package com.onyx.entity;

import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timo
 * thy.osborn on 3/2/15.
 */
@Entity(fileName = "system")
public class SystemRelationship extends AbstractSystemEntity implements IManagedEntity
{

    public SystemRelationship()
    {

    }

    public SystemRelationship(RelationshipDescriptor relationshipDescriptor, SystemEntity entity)
    {
        id = entity.getName() + relationshipDescriptor.getName() +  inverseClass + inverse;
        this.entity = entity;
        this.cascadePolicy = relationshipDescriptor.getCascadePolicy().ordinal();
        this.fetchPolicy = relationshipDescriptor.getFetchPolicy().ordinal();
        this.inverse = relationshipDescriptor.getInverse();
        this.inverseClass = relationshipDescriptor.getInverseClass().getName();
        this.relationshipType = relationshipDescriptor.getRelationshipType().ordinal();
        this.name = relationshipDescriptor.getName();
        this.parentClass = relationshipDescriptor.getParentClass().getName();
        this.loadFactor = relationshipDescriptor.getLoadFactor();
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 1)
    protected int primaryKey;

    @Attribute
    @Index(loadFactor = 1)
    protected String id;

    @Attribute
    protected String name;

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "relationships", inverseClass = SystemEntity.class, loadFactor = 1)
    protected SystemEntity entity;

    @Attribute
    protected String inverse;

    @Attribute
    protected String inverseClass;

    @Attribute
    protected String parentClass;

    @Attribute
    protected int fetchPolicy;

    @Attribute
    protected int cascadePolicy;

    @Attribute
    protected int relationshipType;

    @Attribute
    protected int loadFactor;

    public int getLoadFactor() {
        return loadFactor;
    }

    public void setLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public SystemEntity getEntity()
    {
        return entity;
    }

    public void setEntity(SystemEntity entity)
    {
        this.entity = entity;
    }

    public String getInverse()
    {
        return inverse;
    }

    public void setInverse(String inverse)
    {
        this.inverse = inverse;
    }

    public String getInverseClass()
    {
        return inverseClass;
    }

    public void setInverseClass(String inverseClass)
    {
        this.inverseClass = inverseClass;
    }

    public String getParentClass()
    {
        return parentClass;
    }

    public void setParentClass(String parentClass)
    {
        this.parentClass = parentClass;
    }

    public int getFetchPolicy()
    {
        return fetchPolicy;
    }

    public void setFetchPolicy(int fetchPolicy)
    {
        this.fetchPolicy = fetchPolicy;
    }

    public int getCascadePolicy()
    {
        return cascadePolicy;
    }

    public void setCascadePolicy(int cascadePolicy)
    {
        this.cascadePolicy = cascadePolicy;
    }

    public int getRelationshipType()
    {
        return relationshipType;
    }

    public void setRelationshipType(int relationshipType)
    {
        this.relationshipType = relationshipType;
    }
}
