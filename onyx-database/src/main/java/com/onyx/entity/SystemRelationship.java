package com.onyx.entity;

import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timo
 * tim.osborn on 3/2/15.
 *
 * Relationship information for an entity
 */
@Entity(fileName = "system")
public class SystemRelationship extends ManagedEntity
{

    @SuppressWarnings("unused")
    public SystemRelationship()
    {

    }

    SystemRelationship(RelationshipDescriptor relationshipDescriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.cascadePolicy = relationshipDescriptor.getCascadePolicy().ordinal();
        this.fetchPolicy = relationshipDescriptor.getFetchPolicy().ordinal();
        this.inverse = relationshipDescriptor.getInverse();
        this.inverseClass = relationshipDescriptor.getInverseClass().getName();
        this.relationshipType = relationshipDescriptor.getRelationshipType().ordinal();
        this.name = relationshipDescriptor.getName();
        this.parentClass = relationshipDescriptor.getParentClass().getName();
        this.loadFactor = relationshipDescriptor.getLoadFactor();
        id = entity.getName() + relationshipDescriptor.getName() + inverseClass + inverse + relationshipType;
    }

    @SuppressWarnings("unused")
    @Attribute
    @Identifier(loadFactor = 3)
    protected String id;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String name;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "relationships", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String inverse;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String inverseClass;

    @Attribute
    private String parentClass;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected int fetchPolicy;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected int cascadePolicy;

    @Attribute
    private int relationshipType;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected int loadFactor;

    @SuppressWarnings("unused")
    public int getLoadFactor() {
        return loadFactor;
    }

    @SuppressWarnings("unused")
    public void setLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
    }

    @SuppressWarnings("unused")
    public String getId()
    {
        return id;
    }

    @SuppressWarnings("unused")
    public void setId(String id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    @SuppressWarnings("unused")
    public void setName(String name)
    {
        this.name = name;
    }

    @SuppressWarnings("unused")
    public SystemEntity getEntity()
    {
        return entity;
    }

    @SuppressWarnings("unused")
    public void setEntity(SystemEntity entity)
    {
        this.entity = entity;
    }

    public String getInverse()
    {
        return inverse;
    }

    @SuppressWarnings("unused")
    public void setInverse(String inverse)
    {
        this.inverse = inverse;
    }

    public String getInverseClass()
    {
        return inverseClass;
    }

    @SuppressWarnings("unused")
    public void setInverseClass(String inverseClass)
    {
        this.inverseClass = inverseClass;
    }

    public String getParentClass()
    {
        return parentClass;
    }

    @SuppressWarnings("unused")
    public void setParentClass(String parentClass)
    {
        this.parentClass = parentClass;
    }

    public int getFetchPolicy()
    {
        return fetchPolicy;
    }

    @SuppressWarnings("unused")
    public void setFetchPolicy(int fetchPolicy)
    {
        this.fetchPolicy = fetchPolicy;
    }

    public int getCascadePolicy()
    {
        return cascadePolicy;
    }

    @SuppressWarnings("unused")
    public void setCascadePolicy(int cascadePolicy)
    {
        this.cascadePolicy = cascadePolicy;
    }

    public int getRelationshipType()
    {
        return relationshipType;
    }

    @SuppressWarnings("unused")
    public void setRelationshipType(int relationshipType)
    {
        this.relationshipType = relationshipType;
    }

    public int hashCode()
    {
        return (id != null) ? id.hashCode() : 0;
    }

    public boolean equals(Object o)
    {
        return (o != null && o instanceof SystemRelationship && ((SystemRelationship) o).id != null && ((SystemRelationship) o).id.equals(id));
    }
}
