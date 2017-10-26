package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.query.QueryCriteriaOperator

import java.util.Date

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
class AllAttributeForFetch : AbstractEntity(), IManagedEntity {
    @Identifier
    @Attribute(size = 130)
    var id: String? = null

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

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = AllAttributeForFetchChild::class, inverse = "parent", cascadePolicy = CascadePolicy.ALL)
    var child: AllAttributeForFetchChild? = null
}
