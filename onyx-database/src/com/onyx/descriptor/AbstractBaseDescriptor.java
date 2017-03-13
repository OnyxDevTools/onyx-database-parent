package com.onyx.descriptor;

import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Field;

/**
 * Created by timothy.osborn on 12/12/14.
 *
 * This is a base descriptor for an attribute.  It defines the properties based on annotation scanning
 */
public abstract class AbstractBaseDescriptor
{

    @SuppressWarnings("WeakerAccess")
    protected String name;
    @SuppressWarnings("WeakerAccess")
    protected Class type;

    // Field used for reflection
    protected OffsetField field;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Class getType()
    {
        return type;
    }

    public void setType(Class type)
    {
        this.type = type;
    }

    /**
     * Get Reflection field
     * @return Reflection field
     * @since 1.3.0 - Effort to cleanup reflection
     */
    public OffsetField getField() {
        return field;
    }

    /**
     * Set field and derive a reflection field
     * @param field Field based on property
     *
     * @since 1.3.0 - Effort to cleanup reflection
     */
    public void setField(Field field)
    {
        this.field = ReflectionUtil.getOffsetField(field);
    }
}
