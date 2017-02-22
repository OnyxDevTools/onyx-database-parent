package com.onyx.entity;

import com.onyx.descriptor.IdentifierDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * System entity for entity identifier
 */
@Entity(fileName = "system")
public class SystemIdentifier extends AbstractSystemEntity implements IManagedEntity
{

    @SuppressWarnings("unused")
    public SystemIdentifier()
    {

    }

    SystemIdentifier(IdentifierDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        id = entity.getName() + descriptor.getName();
        this.loadFactor = descriptor.getLoadFactor();
    }

    @SuppressWarnings("unused")
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    protected int primaryKey;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    @Index(loadFactor = 3)
    protected String id;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String name;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected int generator;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "identifier", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

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

    public int getGenerator()
    {
        return generator;
    }

    @SuppressWarnings("unused")
    public void setGenerator(int generator)
    {
        this.generator = generator;
    }


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
}
