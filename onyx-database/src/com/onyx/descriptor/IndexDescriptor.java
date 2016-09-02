package com.onyx.descriptor;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class IndexDescriptor extends AbstractBaseDescriptor implements BaseDescriptor
{

    protected String type = null;
    protected EntityDescriptor entityDescriptor;

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
            (getType().getName() + getName() + entityDescriptor.getPartition().getPartitionValue()).hashCode();
        }
        return (getType().getName() + getName()).hashCode();
    }

    /**
     * Used for usage within a hashmap
     * @param val
     * @return
     */
    @Override
    public boolean equals(Object val)
    {
        if(val instanceof IndexDescriptor)
        {
            IndexDescriptor comparison = (IndexDescriptor) val;
            if(entityDescriptor.getPartition() != null && comparison.getEntityDescriptor().getPartition() != null)
            {
                return (comparison.getType().getName().equals(getType().getName())
                        && comparison.getName().equals(getName())
                        && comparison.getEntityDescriptor().getPartition().getPartitionValue().equals(entityDescriptor.getPartition().getPartitionValue()));
            }
            else if(entityDescriptor.getPartition() != null)
            {
                return false;
            }
            else if(comparison.getEntityDescriptor().getPartition() != null)
            {
                return false;
            }

            return (((IndexDescriptor) val).getType().getName().equals(getType().getName())
            && ((IndexDescriptor) val).getName().equals(getName()));
        }

        return false;
    }
}
