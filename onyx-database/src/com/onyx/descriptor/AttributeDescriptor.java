package com.onyx.descriptor;

import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Field;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class AttributeDescriptor extends AbstractBaseDescriptor
{
    protected boolean nullable;
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
