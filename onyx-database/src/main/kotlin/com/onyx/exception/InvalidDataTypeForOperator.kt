package com.onyx.exception

/**
 * Created by timothy.osborn on 1/3/15.
 *
 */
class InvalidDataTypeForOperator(message: String) : OnyxException(message) {

    companion object {

        @JvmField val INVALID_DATA_TYPE_FOR_OPERATOR = "Invalid Data Type to be used for comparison operator"
    }
}
