package com.onyx.descriptor;

import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.RelationshipType;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Details on an entity relationship
 */
public class RelationshipDescriptor extends AbstractBaseDescriptor
{
    public RelationshipDescriptor(){
        super();
    }

    private RelationshipType relationshipType;
    @SuppressWarnings("WeakerAccess")
    protected String inverse;
    @SuppressWarnings("WeakerAccess")
    protected Class inverseClass;
    private Class parentClass;
    @SuppressWarnings("WeakerAccess")
    protected FetchPolicy fetchPolicy;
    @SuppressWarnings("WeakerAccess")
    protected CascadePolicy cascadePolicy;
    @SuppressWarnings("WeakerAccess")
    protected EntityDescriptor entityDescriptor;

    @SuppressWarnings("WeakerAccess")
    protected byte loadFactor = 1;

    /**
     * This method is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @param loadFactor Value from 1-10.
     * @since 1.2.0
     */
    public void setLoadFactor(byte loadFactor) {
        this.loadFactor = loadFactor;
    }

    /**
     * Getter for the load factor
     *
     * @return The values are from 1-10.
     * <p>
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     * @since 1.2.0
     */
    public byte getLoadFactor() {
        return this.loadFactor;
    }

    public RelationshipType getRelationshipType()
    {
        return relationshipType;
    }

    void setRelationshipType(RelationshipType relationshipType)
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

    void setParentClass(Class parentClass)
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
     * Used for calculating hash structure
     *
     * @return Hash Code of a relationship
     */
    @Override
    public int hashCode()
    {
        if(entityDescriptor.getPartition() != null)
        {
            return (getParentClass().getName() + getName() + getInverseClass().getName() + getInverse() + entityDescriptor.getPartition().getPartitionValue()).hashCode();
        }
        return (getParentClass().getName() + name + getInverseClass().getName() + getInverse()).hashCode();
    }

    /**
     * Used for usage within a hashmap
     * @param val Value to compare
     * @return Whether the parameter is equal to this
     */
    @Override
    public boolean equals(Object val)
    {
        if(val instanceof RelationshipDescriptor)
        {
            RelationshipDescriptor comparison = (RelationshipDescriptor) val;
            if(entityDescriptor.getPartition() != null && comparison.getEntityDescriptor().getPartition() != null)
            {
                return (((RelationshipDescriptor) val).getParentClass().getName().equals(getParentClass().getName())
                        && ((RelationshipDescriptor) val).getName().equals(getName())
                        && ((RelationshipDescriptor) val).getInverseClass().getName().equals(getInverseClass().getName())
                        && ((RelationshipDescriptor) val).getInverse().equals(getInverse())
                        && entityDescriptor.getPartition().getPartitionValue().equals(comparison.getEntityDescriptor().getPartition().getPartitionValue())
                        && comparison.loadFactor == this.loadFactor);
            }
            else if(entityDescriptor.getPartition() != null)
            {
                return false;
            }
            else if(comparison.getEntityDescriptor().getPartition() != null)
            {
                return false;
            }

            return (((RelationshipDescriptor) val).getParentClass().getName().equals(getParentClass().getName())
            && ((RelationshipDescriptor) val).getName().equals(getName())
            && ((RelationshipDescriptor) val).getInverseClass().getName().equals(getInverseClass().getName())
            && ((RelationshipDescriptor) val).getInverse().equals(getInverse())
            && comparison.loadFactor == this.loadFactor);
        }

        return false;
    }
}
