package com.onyx.diskmap

/** Controls whether replacement values may reuse their currently allocated frame. */
enum class ValueUpdateMode {
    /** Allocate a replacement frame and retire the previous one after its pointer changes. */
    APPEND,

    /**
     * Overwrite only when the complete serialized frame has exactly the same length.
     * Size changes retain the ordinary allocation behavior. Overwrites are not crash-atomic;
     * callers must provide recovery or use this mode for rebuildable derived data.
     */
    OVERWRITE_SAME_SIZE
}
