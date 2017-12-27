package com.onyx.exception

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Created by Tim Osborn on 12/31/15.
 *
 */
class UnknownDatabaseException @JvmOverloads constructor(e: Exception? = null) : OnyxException() {
    private var unknownCause: String? = null
    private var stack:String = ""

    init {
        this.unknownCause = e?.message

        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        e?.printStackTrace(printWriter)
        this.stack = stringWriter.toString()
    }
}
