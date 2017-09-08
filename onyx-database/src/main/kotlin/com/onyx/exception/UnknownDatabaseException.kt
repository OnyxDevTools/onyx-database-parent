package com.onyx.exception

/**
 * Created by tosborn1 on 12/31/15.
 *
 */
class UnknownDatabaseException @JvmOverloads constructor(e: Exception? = null) : OnyxException() {
    private var unknownCause: String? = null

    init {
        this.unknownCause = e?.message
    }
}
