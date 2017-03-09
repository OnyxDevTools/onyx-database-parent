package com.onyx.entity;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Index information for entity
 */
@Entity(fileName = "system")
public class SystemIndex extends ManagedEntity
{
    @SuppressWarnings("unused")
    public SystemIndex()
    {

    }

    SystemIndex(IndexDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        id = entity.getName() + descriptor.getName();
        type = descriptor.getType().getName();
        this.loadFactor = descriptor.getLoadFactor();
        this.primaryKey = this.type + this.id;
    }

    @SuppressWarnings("unused")
    @Attribute
    @Identifier(loadFactor = 3)
    protected String primaryKey;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    @Index(loadFactor = 3)
    protected String id;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String name;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "indexes", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String type;

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

    public String getType() {
        return type;
    }

    @SuppressWarnings("unused")
    public void setType(String type) {
        this.type = type;
    }
}
