package com.onyx.exception

/**
 * Exception thrown when the persistence manager is unable to instantiate the aggregator
 */
class StreamException @JvmOverloads constructor(message: String = "") : OnyxException(message) {
    companion object {
        const val CANNOT_INSTANTIATE_STREAM = "Unable to instantiate stream.  Define a valid constructor."
        const val UNSUPPORTED_FUNCTION_ALTERNATIVE = "Unable to instantiate stream.  This function is unsupported."
    }
}
