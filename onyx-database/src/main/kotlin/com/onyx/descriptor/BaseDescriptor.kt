package com.onyx.descriptor

import com.onyx.util.OffsetField

interface BaseDescriptor {

    /**
     * Property Name
     */
    val name: String

    /**
     * Get the reflection field associated to the descriptor
     */
    val field: OffsetField
}
