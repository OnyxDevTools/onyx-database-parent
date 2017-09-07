package com.onyx.exception

/**
 * Created by cosborn on 12/29/2014.
 *
 * Exception occurred while invoking a callback
 */
class EntityCallbackException(methodName: String, message: String, cause: Throwable) : OnyxException(message + methodName, cause) {

    companion object {
        @JvmField val INVOCATION = "Exception occurred when invoking callback: "
    }
}
