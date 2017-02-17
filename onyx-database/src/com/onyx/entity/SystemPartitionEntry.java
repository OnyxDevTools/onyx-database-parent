package com.onyx.entity;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.PartitionDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;


/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "system")
public class SystemPartitionEntry extends AbstractSystemEntity implements IManagedEntity
{
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

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    protected int primaryKey;

    @Index(loadFactor = 3)
    @Attribute
    protected String id;

    @Attribute(size = 1024)
    protected String value;

    @Attribute(size = 2048)
    protected String fileName;

    @Attribute
    @Index(loadFactor = 3)
    protected long index;

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = SystemPartition.class, inverse = "entries", loadFactor = 3)
    protected SystemPartition partition;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public SystemPartition getPartition()
    {
        return partition;
    }

    public void setPartition(SystemPartition partition)
    {
        this.partition = partition;
    }

    public long getIndex()
    {
        return index;
    }

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
