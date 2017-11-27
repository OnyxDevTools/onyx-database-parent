package com.onyx.exception

import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter

/**
 * Created by Tim Osborn on 6/25/16.
 *
 * Base server exception
 */
abstract class OnyxServerException(override var message: String? = "", override final var cause:Throwable? = null) : OnyxException(message, cause), Serializable {

    protected var stack:String = ""

    init {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        cause?.printStackTrace(pw)
        this.stack = sw.toString()
    }
}
