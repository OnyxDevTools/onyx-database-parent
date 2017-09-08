package com.onyx.exception

/**
 * Created by timothy.osborn on 12/12/14.
 *
 */
class InvalidIndexException @JvmOverloads constructor(message: String? = "") : OnyxException(message) {

    companion object {
        @JvmField val INDEX_MISSING_FIELD = "Index is missing attribute"
    }
}
