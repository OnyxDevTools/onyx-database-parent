package com.onyx.entity

import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.IdentifierGenerator

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Created by timothy.osborn on 4/9/15.
 *
 * System error.  To be logged when logging gets to be implemented
 */
@Entity(fileName = "error")
data class SystemError @JvmOverloads constructor(

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null,

    @Attribute
    var packageClass: String? = null,

    @Attribute
    var operation: String? = null,

    @Attribute(size = 20000)
    var message: String? = null,

    @Attribute
    var type: String = ""

) : AbstractSystemEntity() {

    @Suppress("UNUSED")
    constructor(e: Throwable):this() {
        this.exception = e
        this.message = exception!!.message

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        this.exception!!.printStackTrace(pw)

        message = sw.toString()
        type = exception!!.javaClass.name
    }

    var exception: Throwable? = null
}
