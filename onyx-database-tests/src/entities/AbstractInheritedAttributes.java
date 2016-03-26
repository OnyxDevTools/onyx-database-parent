package entities;

import com.onyx.persistence.annotations.Attribute;

import java.util.Date;

/**
 * Created by timothy.osborn on 11/4/14.
 */
public class AbstractInheritedAttributes extends AbstractEntity
{
    @Attribute
    public Long longValue;
    @Attribute
    public long longPrimitive;
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
