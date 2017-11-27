package com.onyx.descriptor

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
    lateinit var field: Field


}
