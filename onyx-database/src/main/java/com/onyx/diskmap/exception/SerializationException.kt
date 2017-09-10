package com.onyx.diskmap.exception

import java.io.IOException

/**
 * Created by timothy.osborn on 4/2/15.
 *
 * Error while serializing object
 */
class SerializationException : IOException(CHECKSUM) {
    companion object {
        private val CHECKSUM = "Invalid serialization checksum"
    }
}
