package entities;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.util.ReflectionUtil;

import java.io.IOException;
import java.util.Date;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity(cachable = false, cacheSize = 100000, fileName = "allattribute.dat")
public class AllAttributeEntity extends AbstractEntity implements IManagedEntity
{
    @Identifier
    @Attribute(size = 64)
    public String id;

    @Attribute(nullable=true)
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
    public Byte amutableByte;
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
    public AllAttributeEntity entity;
    @Attribute
    public byte[] bytes;
    @Attribute
    public short[] shorts;
    @Attribute
    public Byte[] mutableBytes;
    @Attribute
    public Short[] mutableShorts;
    @Attribute
    public String[] strings;

}
