package com.onyx.util;

import java.lang.reflect.Field;

/**
 * Created by tosborn1 on 8/2/16.
 *
 * This field is a wrapper for the unsafe field and the calculated offset
 * It is used for using reflection using the unsafe api.
 *
 */
public class OffsetField
{
    /**
     * Default Constructor with field offset, field name, and class type.
     *
     * @param offset Field unsafe offset
     * @param name Field Name
     * @param type Field Class Type
     */
    public OffsetField(long offset, String name, Field type) {
        this.offset = offset;
        this.name = name;
        this.type = type.getType();
        this.field = type;

        if(!field.isAccessible())
            field.setAccessible(true);
    }

    public long offset;
    public Class type;
    public String name;
    public Field field;

    /**
     * Getter to determine whether the field is an array type
     * @return
     */
    public boolean isArray() {
        return type.isArray();
    }
}