package com.onyx.entity;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.util.OffsetField;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity attribute inforamtion
 */
@Entity(fileName = "system")
public class SystemAttribute extends ManagedEntity
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
        this.primaryKey = dataType + id;
        this.nullable = descriptor.isNullable();
        this.key = descriptor.getName().equals(entity.getIdentifier().getName());
    }

    @SuppressWarnings("unused")
    @Attribute
    @Identifier(loadFactor = 3)
    protected String primaryKey;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String id;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String name;

    @Attribute
    private String dataType;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected int size;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected boolean nullable;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected boolean key;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverse = "attributes", inverseClass = SystemEntity.class, loadFactor = 3)
    protected SystemEntity entity;

    public transient OffsetField field;

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

    public String getDataType()
    {
        return dataType;
    }

    @SuppressWarnings("unused")
    public void setDataType(String type)
    {
        this.dataType = type;
    }

    @SuppressWarnings("unused")
    public int getSize()
    {
        return size;
    }

    @SuppressWarnings("unused")
    public void setSize(int size)
    {
        this.size = size;
    }

    @SuppressWarnings("unused")
    public boolean isNullable()
    {
        return nullable;
    }

    @SuppressWarnings("unused")
    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    @SuppressWarnings("unused")
    public boolean isKey() {
        return key;
    }

    @SuppressWarnings("unused")
    public void setKey(boolean key) {
        this.key = key;
    }

    @SuppressWarnings("unused")
    public String getPrimaryKey() {
        return primaryKey;
    }

    @SuppressWarnings("unused")
    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }
}
