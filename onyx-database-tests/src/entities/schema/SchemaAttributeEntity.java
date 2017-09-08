package entities.schema;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.Date;

/**
 * Created by tosborn1 on 8/23/15.
 */
@Entity
public class SchemaAttributeEntity extends ManagedEntity implements IManagedEntity
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

    /**
     * @see schemaupdate.TestAttributeUpdate
     * Un Comment for initializeTestWithBasicAttribute
     */
//    @Attribute
//    public String addedAttribute;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "parent", inverseClass = SchemaRelationshipEntity.class, cascadePolicy = CascadePolicy.SAVE)
//    @Relationship(type = RelationshipType.MANY_TO_ONE, inverse = "parent", inverseClass = SchemaRelationshipEntity.class, cascadePolicy = CascadePolicy.SAVE)
    public SchemaRelationshipEntity child = null;

}
