package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

import java.util.Date

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
class PerformanceEntity : AbstractEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

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
    @Index
    var idValue: Long = 0

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = PerformanceEntityChild::class, inverse = "parent", cascadePolicy = CascadePolicy.ALL)
    var child: PerformanceEntityChild? = null

}
