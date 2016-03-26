package embedded.performance.types ;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import entities.AbstractEntity;

import java.util.Date;

/**
 *  Created by cosbor11 on 01/8/14.
 */
@Entity
public class OnyxEntity extends AbstractEntity implements IManagedEntity
{
    @Identifier
    @Attribute(size = 130)
    public String id;

    @Attribute(nullable=false)
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

}
