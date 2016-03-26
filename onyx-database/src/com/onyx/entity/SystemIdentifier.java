package com.onyx.entity;

import com.onyx.descriptor.IdentifierDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/2/15.
 */

@Entity(fileName = "system")
public class SystemIdentifier extends AbstractSystemEntity implements IManagedEntity
{

    public SystemIdentifier()
    {

    }

    public SystemIdentifier(IdentifierDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        id = entity.getName() + descriptor.getName();
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected int primaryKey;

    @Attribute
    @Index
    protected String id;

    @Attribute
    protected String name;

    @Attribute
    protected int generator;

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "identifier", inverseClass = SystemEntity.class)
    protected SystemEntity entity;

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

    public int getGenerator()
    {
        return generator;
    }

    public void setGenerator(int generator)
    {
        this.generator = generator;
    }
}
