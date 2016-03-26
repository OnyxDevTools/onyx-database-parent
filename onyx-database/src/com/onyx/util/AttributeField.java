package com.onyx.util;

import java.lang.reflect.Field;

/**
 * Created by timothy.osborn on 2/11/15.
 */
public class AttributeField
{
    private static ObjectUtil reflection = ObjectUtil.getInstance();

    public AttributeField(Field field)
    {
        this.field = field;
        if (!field.isAccessible())
        {
            field.setAccessible(true);
        }
        this.offset = reflection.getAttributeOffset(field);
        this.type = field.getType();
    }

    public Field field;
    public long offset;
    public Class type;
}
