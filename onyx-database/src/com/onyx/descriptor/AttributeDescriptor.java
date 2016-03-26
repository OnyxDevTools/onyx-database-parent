package com.onyx.descriptor;

import com.onyx.util.AttributeField;

import java.lang.reflect.Field;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class AttributeDescriptor extends AbstractBaseDescriptor
{
    protected boolean nullable;
    protected int size;
    public AttributeField field;

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
        this.field = new AttributeField(field);
    }
}
