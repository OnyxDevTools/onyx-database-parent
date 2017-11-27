package com.onyx.exception

/**
 * Created by Chris Osborn on 12/29/2014.
 *
 * Exception occurred while invoking a callback
 */
class EntityCallbackException @JvmOverloads constructor(methodName: String? = "", message: String? = "", cause: Throwable? = null) : OnyxException(message + methodName, cause) {

    companion object {
        @JvmField val INVOCATION = "Exception occurred when invoking callback: "
    }
}
