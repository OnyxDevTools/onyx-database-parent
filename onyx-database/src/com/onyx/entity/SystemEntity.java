package com.onyx.entity;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 3/2/15.
 */
@Entity(fileName = "system")
public class SystemEntity extends AbstractSystemEntity implements IManagedEntity
{
    public SystemEntity()
    {

    }

    public SystemEntity(EntityDescriptor descriptor)
    {
        this.name = descriptor.getClazz().getCanonicalName();
        this.className = descriptor.getClazz().getSimpleName();
        this.indexes = new ArrayList<>();
        this.relationships = new ArrayList<>();
        this.attributes = new ArrayList<>();
        this.identifier = new SystemIdentifier(descriptor.getIdentifier(), this);

        for(AttributeDescriptor attributeDescriptor : descriptor.getAttributes().values())
        {
            this.attributes.add(new SystemAttribute(attributeDescriptor, this));
        }

        for(RelationshipDescriptor relationshipDescriptor : descriptor.getRelationships().values())
        {
            this.relationships.add(new SystemRelationship(relationshipDescriptor, this));
        }

        for(IndexDescriptor indexDescriptor : descriptor.getIndexes().values())
        {
            this.indexes.add(new SystemIndex(indexDescriptor, this));
        }

        if(descriptor.getPartition() != null)
        {
            this.partition = new SystemPartition(descriptor.getPartition(), this);
        }
    }

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected int primaryKey;

    @Index
    @Attribute
    protected String name;

    @Attribute
    protected String className;

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverse = "entity", inverseClass = SystemIdentifier.class)
    protected SystemIdentifier identifier;

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverse = "entity", inverseClass = SystemPartition.class)
    protected SystemPartition partition;

    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemAttribute.class, inverse = "entity")
    protected List<SystemAttribute> attributes;

    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemRelationship.class, inverse = "entity")
    protected List<SystemRelationship> relationships;

    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemIndex.class, inverse = "entity")
    protected List<SystemIndex> indexes;

    public int getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(int id) {
        this.primaryKey = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public SystemIdentifier getIdentifier()
    {
        return identifier;
    }

    public void setIdentifier(SystemIdentifier identifier)
    {
        this.identifier = identifier;
    }

    public SystemPartition getPartition()
    {
        return partition;
    }

    public void setPartition(SystemPartition partition)
    {
        this.partition = partition;
    }

    public List<SystemAttribute> getAttributes()
    {
        return attributes;
    }

    public void setAttributes(List<SystemAttribute> attributes)
    {
        this.attributes = attributes;
    }

    public List<SystemRelationship> getRelationships()
    {
        return relationships;
    }

    public void setRelationships(List<SystemRelationship> relationship)
    {
        this.relationships = relationship;
    }

    public List<SystemIndex> getIndexes()
    {
        return indexes;
    }

    public void setIndexes(List<SystemIndex> indexes)
    {
        this.indexes = indexes;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }
}
