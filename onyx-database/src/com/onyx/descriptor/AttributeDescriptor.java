package com.onyx.descriptor;

import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Field;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * This is a base descriptor for an attribute.  It defines the properties based on annotation scanning
 */
public class AttributeDescriptor extends AbstractBaseDescriptor
{
    @SuppressWarnings("WeakerAccess")
    protected boolean nullable;
    @SuppressWarnings("WeakerAccess")
    protected int size;
    public OffsetField field;

    public boolean isNullable()
    {
        return nullable;
    }

    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    public int getSize()
    {
        return size;
    }

    public void setSize(int size)
    {
        this.size = size;
    }

    public void setField(Field field)
    {
        this.field = ReflectionUtil.getOffsetField(field);
    }
}
