package com.onyx.entity;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.util.OffsetField;

import java.util.ArrayList;
import java.util.List;

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
        this.entities = new ArrayList<>();
        this.entities.add(entity);
        this.name = descriptor.getName();
        this.id = entity.getName() + descriptor.getName();
        this.size = descriptor.getSize();
        this.dataType = descriptor.getType().getName();
        this.primaryKey = dataType + id;
        this.nullable = descriptor.isNullable();
        this.key = descriptor.getName().equals(entity.getIdentifier().getName());
        this.isEnum = descriptor.isEnum();
        this.enumValues = descriptor.getEnumValues();
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

    @Attribute
    private boolean isEnum;

    @Attribute
    private String enumValues;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.MANY_TO_MANY, fetchPolicy = FetchPolicy.NONE, cascadePolicy = CascadePolicy.SAVE, inverse = "attributes", inverseClass = SystemEntity.class, loadFactor = 3)
    protected List<SystemEntity> entities;

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
    public List<SystemEntity> getEntities()
    {
        return entities;
    }

    @SuppressWarnings("unused")
    public void setEntities(List<SystemEntity> entities)
    {
        this.entities = entities;
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

    public boolean isEnum() {
        return isEnum;
    }

    @SuppressWarnings("unused")
    public void setEnum(boolean anEnum) {
        isEnum = anEnum;
    }

    public String getEnumValues() {
        return enumValues;
    }

    @SuppressWarnings("unused")
    public void setEnumValues(String enumValues) {
        this.enumValues = enumValues;
    }
}
