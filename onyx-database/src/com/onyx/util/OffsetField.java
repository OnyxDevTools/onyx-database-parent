package com.onyx.util;

import java.lang.reflect.Field;

/**
 * Created by tosborn1 on 8/2/16.
 *
 * This field is a wrapper for the unsafe field and the calculated offset
 * It is used for using reflection using the unsafe api.
 *
 */
public class OffsetField {

    /**
     * Default Constructor with field offset, field name, and class type.
     *
     * @param name   Field Name
     * @param type   Field Class Type
     */
    public OffsetField(String name, Field type) {
        this.name = name;
        this.type = type.getType();
        this.field = type;

        if(!this.field.isAccessible())
            this.field.setAccessible(true);
    }


    public final Class type;
    public final String name;
    public final Field field;

    /**
     * Getter to determine whether the field is an array type
     *
     * @return Whether the field is an array
     */
    @SuppressWarnings("unused")
    public boolean isArray() {
        return type.isArray();
    }


}