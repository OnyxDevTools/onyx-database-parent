package pojo

import java.io.Serializable
import java.util.Date

/**
 * Created by timothy.osborn on 4/14/15.
 */
class AllTypes : Serializable {

    var intValue: Int = 0
    var intValueM: Int? = null
    var longValue: Long = 0
    var longValueM: Long? = null
    var booleanValue: Boolean = false
    var booleanValueM: Boolean? = null
    var shortValue: Short = 0
    var shortValueM: Short? = null
    var doubleValue: Double = 0.toDouble()
    var doubleValueM: Double? = null
    var floatValue: Float = 0.toFloat()
    var floatValueM: Float? = null
    var byteValue: Byte = 0
    var byteValueM: Byte? = null
    var dateValue: Date? = null
    var stringValue: String? = null
    var nullValue: Any? = null
    var charValue: Char = ' '
    var charValueM: Char? = null

    override fun hashCode(): Int = intValue

    override fun equals(`val`: Any?): Boolean = if (`val` is AllTypes) {
        `val`.intValue == intValue
    } else false
}
