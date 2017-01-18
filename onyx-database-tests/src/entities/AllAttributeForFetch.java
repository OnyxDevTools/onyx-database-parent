package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.query.QueryCriteriaOperator;

import java.util.Date;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class AllAttributeForFetch extends AbstractEntity implements IManagedEntity
{
    @Identifier
    @Attribute(size = 130)
    public String id;

    @Attribute
    public Long longValue;
    @Attribute
    public long longPrimitive;
    @Attribute
    public Integer intValue;
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


    @Attribute
    public Float mutableFloat;
    @Attribute
    public float floatValue;
    @Attribute
    public Byte mutableByte;
    @Attribute
    public byte byteValue;
    @Attribute
    public Short mutableShort;
    @Attribute
    public short shortValue;
    @Attribute
    public Character mutableChar;
    @Attribute
    public char charValue;
    @Attribute
    public AllAttributeV2Entity entity;
    @Attribute
    public QueryCriteriaOperator operator;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = AllAttributeForFetchChild.class, inverse = "parent", cascadePolicy = CascadePolicy.ALL)
    public AllAttributeForFetchChild child;
}
