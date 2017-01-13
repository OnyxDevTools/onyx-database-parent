package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.Date;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity(cachable = true, cacheSize = 35)
public class PerformanceEntity extends AbstractEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
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

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = PerformanceEntityChild.class, inverse = "parent", cascadePolicy = CascadePolicy.ALL)
    public PerformanceEntityChild child;

    public boolean equals(Object o)
    {
        return (o instanceof PerformanceEntity && ((PerformanceEntity) o).id.equals(id));
    }
}
