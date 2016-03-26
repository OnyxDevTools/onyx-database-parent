package com.onyx.descriptor;

import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.RelationshipType;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class RelationshipDescriptor extends AbstractBaseDescriptor
{
    public RelationshipDescriptor(){
        super();
    }

    protected RelationshipType relationshipType;
    protected String inverse;
    protected Class inverseClass;
    protected Class parentClass;
    protected FetchPolicy fetchPolicy;
    protected CascadePolicy cascadePolicy;
    protected EntityDescriptor entityDescriptor;

    public RelationshipType getRelationshipType()
    {
        return relationshipType;
    }

    public void setRelationshipType(RelationshipType relationshipType)
    {
        this.relationshipType = relationshipType;
    }

    public String getInverse()
    {
        return inverse;
    }

    public void setInverse(String inverse)
    {
        this.inverse = inverse;
    }

    public Class getInverseClass()
    {
        return inverseClass;
    }

    public void setInverseClass(Class inverseClass)
    {
        this.inverseClass = inverseClass;
    }

    public Class getParentClass()
    {
        return parentClass;
    }

    public void setParentClass(Class parentClass)
    {
        this.parentClass = parentClass;
    }

    public FetchPolicy getFetchPolicy()
    {
        return fetchPolicy;
    }

    public void setFetchPolicy(FetchPolicy fetchPolicy)
    {
        this.fetchPolicy = fetchPolicy;
    }

    public CascadePolicy getCascadePolicy()
    {
        return cascadePolicy;
    }

    public void setCascadePolicy(CascadePolicy cascadePolicy)
    {
        this.cascadePolicy = cascadePolicy;
    }

    public EntityDescriptor getEntityDescriptor()
    {
        return entityDescriptor;
    }

    public void setEntityDescriptor(EntityDescriptor entityDescriptor)
    {
        this.entityDescriptor = entityDescriptor;
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
        if(entityDescriptor.getPartition() != null)
        {
            return (getParentClass().getCanonicalName() + getName() + getInverseClass().getCanonicalName() + getInverse() + entityDescriptor.getPartition().getPartitionValue()).hashCode();
        }
        return (getParentClass().getCanonicalName() + name + getInverseClass().getCanonicalName() + getInverse()).hashCode();
    }

    /**
     * Used for usage within a hashmap
     * @param val
     * @return
     */
    @Override
    public boolean equals(Object val)
    {
        if(val instanceof RelationshipDescriptor)
        {
            RelationshipDescriptor comparison = (RelationshipDescriptor) val;
            if(entityDescriptor.getPartition() != null && comparison.getEntityDescriptor().getPartition() != null)
            {
                return (((RelationshipDescriptor) val).getParentClass().getCanonicalName().equals(getParentClass().getCanonicalName())
                        && ((RelationshipDescriptor) val).getName().equals(getName())
                        && ((RelationshipDescriptor) val).getInverseClass().getCanonicalName().equals(getInverseClass().getCanonicalName())
                        && ((RelationshipDescriptor) val).getInverse().equals(getInverse())
                        && entityDescriptor.getPartition().getPartitionValue().equals(comparison.getEntityDescriptor().getPartition().getPartitionValue()));
            }
            else if(entityDescriptor.getPartition() != null)
            {
                return false;
            }
            else if(comparison.getEntityDescriptor().getPartition() != null)
            {
                return false;
            }

            return (((RelationshipDescriptor) val).getParentClass().getCanonicalName().equals(getParentClass().getCanonicalName())
            && ((RelationshipDescriptor) val).getName().equals(getName())
            && ((RelationshipDescriptor) val).getInverseClass().getCanonicalName().equals(getInverseClass().getCanonicalName())
            && ((RelationshipDescriptor) val).getInverse().equals(getInverse()));
        }

        return false;
    }
}
