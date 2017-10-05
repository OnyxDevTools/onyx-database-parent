package entities;

import com.onyx.extension.common.Any_ComparisonKt;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.query.QueryCriteriaOperator;

import java.util.List;
import java.util.Set;

/**
 * Created by tosborn1 on 1/18/17.
 */
@Entity
public class AllAttributeV2Entity extends AllAttributeEntity {

    @Identifier
    @Attribute(size = 64)
    public String id;

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
    @Attribute
    public byte[] bytes;
    @Attribute
    public short[] shorts;
    @Attribute
    public Byte[] amutableBytes;
    @Attribute
    public Short[] mutableShorts;
    @Attribute
    public String[] strings;
    @Attribute
    public List<AllAttributeEntity> entityList;
    @Attribute
    public Set<AllAttributeEntity> entitySet;

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof AllAttributeV2Entity && Any_ComparisonKt.forceCompare(((AllAttributeV2Entity) o).id, id));
    }

    @Override
    public int hashCode() {
        if(id == null) return 0; else return id.hashCode();
    }
}
