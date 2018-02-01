package com.onyx.exception

/**
 * Exception thrown when the persistence manager is unable to instantiate the aggregator
 */
class StreamException @JvmOverloads constructor(message: String = "") : OnyxException(message) {
    companion object {
        @JvmField val CANNOT_INSTANTIATE_STREAM = "Unable to instantiate stream.  Define a valid constructor."
        @JvmField val UNSUPPORTED_FUNCTION_ALTERNATIVE = "Unable to instantiate stream.  This function is unsupported."
    }
}
