package entities.schema;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.Date;

/**
 * Created by tosborn1 on 8/23/15.
 */
@Entity
public class SchemaRelationshipEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier
    @Attribute(size = 64)
    public String id;

    @Attribute(nullable=true)
    public Long longValue;
    @Attribute
    public long longPrimitive;
    @Attribute
    public int intValue;
    @Attribute
    public int intPrimitive;
    @Attribute
    public String stringValue;
    @Attribute
    public Date dateValue;
    @Attribute
    public Double doubleValue;
    @Attribute
    public double doublePrimitive;
    @Attribute
    public Boolean booleanValue;
    @Attribute
    public boolean booleanPrimitive;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "child", inverseClass = SchemaAttributeEntity.class)
    public SchemaAttributeEntity parent = null;

//    @Relationship(type = RelationshipType.ONE_TO_MANY, inverse = "child", inverseClass = SchemaAttributeEntity.class)
//    public List<SchemaAttributeEntity> parent = null;
    /**
     * @see schemaupdate.TestRelationshipUpdate
     * Un Comment for initializeTestWithBasicAttribute
     */
    @Attribute
    public String addedAttribute;

//    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = SchemaAttributeEntity.class)
//    public SchemaAttributeEntity addedRelationship = null;
}
