package com.onyx.entity;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Index information for entity
 */
@Entity(fileName = "system")
public class SystemIndex extends AbstractSystemEntity implements IManagedEntity
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
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    protected int primaryKey;

    @Attribute
    @Index(loadFactor = 3)
    protected String id;

    @Attribute
    protected String name;

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "indexes", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

    @Attribute
    protected String type;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
