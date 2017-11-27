package com.onyx.descriptor

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * This is a base descriptor for an attribute.  It defines the properties based on annotation scanning
 */
data class AttributeDescriptor(
    var isNullable: Boolean = false,
    var size: Int = 0,
    var isEnum: Boolean = false,
    var enumValues: String? = null,
    var name: String = "",
    var type: Class<*> = Any::class.java
) : AbstractBaseDescriptor()
