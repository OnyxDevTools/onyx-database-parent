package com.onyx.entity;

import com.onyx.descriptor.PartitionDescriptor;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Partition information for an entity
 */
@Entity(fileName = "system")
public class SystemPartition extends ManagedEntity
{

    @SuppressWarnings("unused")
    public SystemPartition()
    {

    }

    public SystemPartition(PartitionDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        id = entity.getName() + descriptor.getName();
    }

    @SuppressWarnings("unused")
    @Attribute
    @Identifier(loadFactor = 3)
    protected String id;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String name;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "partition", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.SAVE, inverse = "partition", inverseClass = SystemPartitionEntry.class, fetchPolicy = FetchPolicy.EAGER, loadFactor = 3)
    private List<SystemPartitionEntry> entries = new ArrayList<>();

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

    public List<SystemPartitionEntry> getEntries()
    {
        return entries;
    }

    @SuppressWarnings("unused")
    public void setEntries(List<SystemPartitionEntry> entries)
    {
        this.entries = entries;
    }
}
