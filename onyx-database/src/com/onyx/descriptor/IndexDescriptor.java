package com.onyx.descriptor;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class IndexDescriptor extends AbstractBaseDescriptor implements BaseDescriptor {

    protected String type = null;
    protected EntityDescriptor entityDescriptor;

    public EntityDescriptor getEntityDescriptor() {
        return entityDescriptor;
    }

    public void setEntityDescriptor(EntityDescriptor entityDescriptor) {
        this.entityDescriptor = entityDescriptor;
    }

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

    ////////////////////////////////////////////////////////
    //
    //  Hashing Object Overrides
    //
    ////////////////////////////////////////////////////////

    /**
     * Used for calculating hash structure
     *
     * @return
     */
    @Override
    public int hashCode() {
        if (entityDescriptor.getPartition() != null) {
            (getType().getName() + getName() + entityDescriptor.getPartition().getPartitionValue()).hashCode();
        }
        return (getType().getName() + getName() + entityDescriptor.getClazz().getName()).hashCode();
    }

    /**
     * Used for usage within a hashmap
     *
     * @param val
     * @return
     */
    @Override
    public boolean equals(Object val) {
        if (val instanceof IndexDescriptor) {
            IndexDescriptor comparison = (IndexDescriptor) val;

            if (((IndexDescriptor) val).loadFactor != this.loadFactor)
                return false;

            if (!entityDescriptor.clazz.getName().equals(comparison.entityDescriptor.clazz.getName())) {
                return false;
            }

            if (entityDescriptor.getPartition() != null && comparison.getEntityDescriptor().getPartition() != null) {
                return (comparison.getType().getName().equals(getType().getName())
                        && comparison.getName().equals(getName())
                        && comparison.getEntityDescriptor().getPartition().getPartitionValue().equals(entityDescriptor.getPartition().getPartitionValue()));
            } else if (entityDescriptor.getPartition() != null) {
                return false;
            } else if (comparison.getEntityDescriptor().getPartition() != null) {
                return false;
            }


            return (((IndexDescriptor) val).getType().getName().equals(getType().getName())
                    && ((IndexDescriptor) val).getName().equals(getName()));
        }
        else if(val instanceof IdentifierDescriptor)
        {
            return this.loadFactor == ((IdentifierDescriptor) val).loadFactor
                    && this.name.equals(((IdentifierDescriptor) val).name);
        }

        return false;
    }
}
