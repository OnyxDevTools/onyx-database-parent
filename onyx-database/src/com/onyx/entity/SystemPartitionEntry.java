package com.onyx.entity;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.PartitionDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;


/**
 * Created by timothy.osborn on 3/5/15.
 *
 * Partition entity for an entity
 */
@Entity(fileName = "system")
public class SystemPartitionEntry extends AbstractSystemEntity implements IManagedEntity
{

    @SuppressWarnings("unused")
    public SystemPartitionEntry()
    {

    }

    public SystemPartitionEntry(EntityDescriptor entityDescriptor, PartitionDescriptor descriptor, SystemPartition partition, long index)
    {
        this.partition = partition;
        this.id = entityDescriptor.getClazz().getName() + descriptor.getPartitionValue();
        this.value = descriptor.getPartitionValue();
        this.fileName = entityDescriptor.getFileName() + descriptor.getPartitionValue();
        this.index = index;
    }

    @SuppressWarnings("unused")
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    protected int primaryKey;

    @SuppressWarnings("WeakerAccess")
    @Index(loadFactor = 3)
    @Attribute
    protected String id;

    @SuppressWarnings("WeakerAccess")
    @Attribute(size = 1024)
    protected String value;

    @SuppressWarnings("WeakerAccess")
    @Attribute(size = 2048)
    protected String fileName;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    @Index(loadFactor = 3)
    protected long index;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = SystemPartition.class, inverse = "entries", loadFactor = 3)
    protected SystemPartition partition;

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

    public String getValue()
    {
        return value;
    }

    @SuppressWarnings("unused")
    public void setValue(String value)
    {
        this.value = value;
    }

    @SuppressWarnings("unused")
    public String getFileName()
    {
        return fileName;
    }

    @SuppressWarnings("unused")
    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    @SuppressWarnings("unused")
    public SystemPartition getPartition()
    {
        return partition;
    }

    @SuppressWarnings("unused")
    public void setPartition(SystemPartition partition)
    {
        this.partition = partition;
    }

    public long getIndex()
    {
        return index;
    }

    @SuppressWarnings("unused")
    public void setIndex(int index)
    {
        this.index = index;
    }


    @Override
    public int hashCode()
    {
        if(id == null)
            return 0;
        return id.hashCode();
    }

    @Override
    public boolean equals(Object val)
    {
        if(val instanceof SystemPartitionEntry)
        {
            if(((SystemPartitionEntry) val).id == null && id == null)
                return true;
            if(((SystemPartitionEntry) val).id.equals(id))
                return true;
        }

        return false;
    }
}
