package com.onyx.descriptor

import com.onyx.util.ReflectionField
import com.onyx.util.ReflectionUtil

import java.lang.reflect.Field

/**
 * Created by timothy.osborn on 12/12/14.
 *
 * This is a base descriptor for an attribute.  It defines the properties based on annotation scanning
 */
abstract class AbstractBaseDescriptor {

    /**
     * Get Reflection field
     */
    @Transient
    lateinit var field: ReflectionField

    /**
     * Set field and derive a reflection field
     * @param field Field based on property
     *
     * @since 1.3.0 - Effort to cleanup reflection
     */
    fun setReflectionField(field: Field) {
        this.field = ReflectionUtil.getReflectionField(field)
    }
}
