package com.onyx.entity;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.util.OffsetField;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity attribute inforamtion
 */
@Entity(fileName = "system")
public class SystemAttribute extends AbstractSystemEntity implements IManagedEntity
{
    @SuppressWarnings("unused")
    public SystemAttribute()
    {
    }

    SystemAttribute(AttributeDescriptor descriptor, SystemEntity entity)
    {
        this.entity = entity;
        this.name = descriptor.getName();
        this.id = entity.getName() + descriptor.getName();
        this.size = descriptor.getSize();
        this.dataType = descriptor.getType().getName();
        this.nullable = descriptor.isNullable();
        this.key = descriptor.getName().equals(entity.getIdentifier().getName());
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    protected int primaryKey;

    @Attribute
    protected String id;

    @Attribute
    protected String name;

    @Attribute
    private String dataType;

    @Attribute
    protected int size;

    @Attribute
    protected boolean nullable;

    @Attribute
    protected boolean key;

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "attributes", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

    public transient OffsetField field;

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

    public String getDataType()
    {
        return dataType;
    }

    @SuppressWarnings("unused")
    public void setDataType(String type)
    {
        this.dataType = type;
    }

    public int getSize()
    {
        return size;
    }

    public void setSize(int size)
    {
        this.size = size;
    }

    public boolean isNullable()
    {
        return nullable;
    }

    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    public boolean isKey() {
        return key;
    }

    public void setKey(boolean key) {
        this.key = key;
    }
}
