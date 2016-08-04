package pojo;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by timothy.osborn on 4/14/15.
 */
public class AllTypes implements Serializable
{
    public AllTypes()
    {

    }

    public int intValue;
    public Integer intValueM;
    public long longValue;
    public Long longValueM;
    public boolean booleanValue;
    public Boolean booleanValueM;
    public short shortValue;
    public Short shortValueM;
    public double doubleValue;
    public Double doubleValueM;
    public float floatValue;
    public Float floatValueM;
    public byte byteValue;
    public Byte byteValueM;
    public Date dateValue;
    public String stringValue;
    public Object nullValue;
    public char charValue;
    public Character charValueM;

    public int hashCode()
    {
        return intValue;
    }

    public boolean equals(Object val)
    {
        if(val instanceof AllTypes)
        {
            return ((AllTypes) val).intValue == intValue;
        }

        return false;
    }
}
