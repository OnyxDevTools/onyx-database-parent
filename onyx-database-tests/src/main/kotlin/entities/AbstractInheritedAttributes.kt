package entities

import com.onyx.persistence.annotations.Attribute

import java.util.Date

/**
 * Created by timothy.osborn on 11/4/14.
 */
open class AbstractInheritedAttributes : AbstractEntity() {
    @Attribute
    var longValue: Long? = null
    @Attribute
    var longPrimitive: Long = 0
    @Attribute
    var stringValue: String? = null
    @Attribute
    var dateValue: Date? = null
    @Attribute
    var doubleValue: Double? = null
    @Attribute
    var doublePrimitive: Double = 0.toDouble()
    @Attribute
    var booleanValue: Boolean? = null
    @Attribute
    var booleanPrimitive: Boolean = false

}
