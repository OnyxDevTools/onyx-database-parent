package entities

import com.onyx.buffer.BufferStreamable

import java.io.IOException
import java.util.Date

/**
 * Created by timothy.osborn on 4/2/15.
 */
class EntityYo : BufferStreamable {
    var id: String? = null
    var longValue: Long? = null
    var dateValue: Date? = null
    var longStringValue: String? = null
    var otherStringValue: String? = null
    var mutableInteger: Int? = null
    var mutableLong: Long? = null
    var mutableBoolean: Boolean? = null
    var mutableFloat: Float? = null
    var mutableDouble: Double? = null
    var immutableInteger: Int = 0
    var immutableLong: Long = 0
    var immutableBoolean: Boolean = false
    var immutableFloat: Float = 0.toFloat()
    var immutableDouble: Double = 0.toDouble()
}
