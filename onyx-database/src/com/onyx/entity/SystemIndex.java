package com.onyx.entity;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/2/15.
 */
@Entity(fileName = "system")
public class SystemIndex extends AbstractSystemEntity implements IManagedEntity
{

    public SystemIndex()
    {

    }

    public SystemIndex(IndexDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        id = entity.getName() + descriptor.getName();
        type = descriptor.getType().getSimpleName();
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected int primaryKey;

    @Attribute
    @Index
    protected String id;

    @Attribute
    protected String name;

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "indexes", inverseClass = SystemEntity.class)
    protected SystemEntity entity;

    @Attribute
    protected String type;

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
