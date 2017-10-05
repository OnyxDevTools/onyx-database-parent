package com.onyx.descriptor

import java.lang.reflect.Field

interface BaseDescriptor {

    /**
     * Property Name
     */
    val name: String

    /**
     * Get the reflection field associated to the descriptor
     */
    val field: Field
}
