package com.onyx.entity;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Contains entity information
 */
@Entity(fileName = "system")
public class SystemEntity extends AbstractSystemEntity implements IManagedEntity
{
    public SystemEntity()
    {
    }

    public SystemEntity(EntityDescriptor descriptor)
    {
        this.name = descriptor.getClazz().getName();
        this.className = descriptor.getClazz().getSimpleName();
        this.indexes = new ArrayList<>();
        this.relationships = new ArrayList<>();
        this.attributes = new ArrayList<>();
        this.identifier = new SystemIdentifier(descriptor.getIdentifier(), this);
        this.fileName = descriptor.getFileName();
        this.attributes.addAll(descriptor.getAttributes().values().stream().map(attributeDescriptor -> new SystemAttribute(attributeDescriptor, this)).collect(Collectors.toList()));
        this.relationships.addAll(descriptor.getRelationships().values().stream().map(relationshipDescriptor -> new SystemRelationship(relationshipDescriptor, this)).collect(Collectors.toList()));
        this.indexes.addAll(descriptor.getIndexes().values().stream().map(indexDescriptor -> new SystemIndex(indexDescriptor, this)).collect(Collectors.toList()));

        if(descriptor.getPartition() != null)
        {
            this.partition = new SystemPartition(descriptor.getPartition(), this);
        }

        this.getAttributes().sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        this.getRelationships().sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        this.getIndexes().sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
    }

    @SuppressWarnings("WeakerAccess")
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 3)
    @Attribute
    protected int primaryKey;

    @SuppressWarnings("WeakerAccess")
    @Index(loadFactor = 3)
    @Attribute
    protected String name;

    @Attribute
    private String className;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    private String fileName;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverse = "entity", inverseClass = SystemIdentifier.class, loadFactor = 3)
    protected SystemIdentifier identifier;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverse = "entity", inverseClass = SystemPartition.class, loadFactor = 3)
    protected SystemPartition partition;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemAttribute.class, inverse = "entity", loadFactor = 3)
    protected List<SystemAttribute> attributes;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemRelationship.class, inverse = "entity", loadFactor = 3)
    protected List<SystemRelationship> relationships;

    @SuppressWarnings("WeakerAccess")
    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.ALL, inverseClass = SystemIndex.class, inverse = "entity", loadFactor = 3)
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

    @SuppressWarnings("unused")
    public void setName(String name)
    {
        this.name = name;
    }

    public SystemIdentifier getIdentifier()
    {
        return identifier;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void setAttributes(List<SystemAttribute> attributes)
    {
        this.attributes = attributes;
    }

    public List<SystemRelationship> getRelationships()
    {
        return relationships;
    }

    @SuppressWarnings("unused")
    public void setRelationships(List<SystemRelationship> relationship)
    {
        this.relationships = relationship;
    }

    public List<SystemIndex> getIndexes()
    {
        return indexes;
    }

    @SuppressWarnings("unused")
    public void setIndexes(List<SystemIndex> indexes)
    {
        this.indexes = indexes;
    }

    @SuppressWarnings("unused")
    public String getClassName()
    {
        return className;
    }

    @SuppressWarnings("unused")
    public void setClassName(String className)
    {
        this.className = className;
    }

    public String getFileName() {
        return fileName;
    }

    @SuppressWarnings("unused")
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
