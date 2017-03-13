package com.onyx.descriptor;

import com.onyx.util.OffsetField;

/**
 Created by timothy.osborn on 2/10/15.
 */
public interface BaseDescriptor
{
    /**
     * Gets the attribute name.
     *
     * @return  the name of the descriptor
     */
    String getName();

    /**
     * Get the reflection field associated to the descriptor
     * @return offset field
     *
     * @since 1.3.0
     */
    OffsetField getField();
}
