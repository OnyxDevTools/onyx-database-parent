package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.query.QueryCriteriaOperator;

import java.util.Date;

/**
 * Created by tosborn1 on 3/13/17.
 */
@Entity
public class AllAttributeForFetchSequenceGen extends AbstractEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute(size = 130)
    public Long id;

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

}