package com.onyx.exception

/**
 * Created by tosborn1 on 12/31/15.
 *
 */
class UnknownDatabaseException(e: Exception) : OnyxException() {
    private var unknownCause: String? = null

    init {
        this.unknownCause = e.message
    }
}
