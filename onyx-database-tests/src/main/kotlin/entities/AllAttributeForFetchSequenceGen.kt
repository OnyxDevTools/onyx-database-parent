package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.query.QueryCriteriaOperator

import java.util.Date

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Entity
@Suppress("UNUSED")
class AllAttributeForFetchSequenceGen : AbstractEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute(size = 130)
    var id: Long? = null

    @Index
    @Attribute
    var indexVal: Int = 0

    @Attribute
    var longValue: Long? = null
    @Attribute
    var longPrimitive: Long = 0
    @Attribute
    var intValue: Int? = null
    @Attribute
    var intPrimitive: Int = 0
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


    @Attribute
    var mutableFloat: Float? = null
    @Attribute
    var floatValue: Float = 0.toFloat()
    @Attribute
    var mutableByte: Byte? = null
    @Attribute
    var byteValue: Byte = 0
    @Attribute
    var mutableShort: Short? = null
    @Attribute
    var shortValue: Short = 0
    @Attribute
    var mutableChar: Char? = null
    @Attribute
    var charValue: Char = ' '
    @Attribute
    var entity: AllAttributeV2Entity? = null
    @Attribute
    var operator: QueryCriteriaOperator? = null

}