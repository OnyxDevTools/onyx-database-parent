package entities.schema

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.RelationshipType

import java.util.Date

/**
 * Created by Tim Osborn on 8/23/15.
 */
@Entity
class SchemaAttributeEntity : ManagedEntity(), IManagedEntity {
    @Identifier
    @Attribute(size = 64)
    var id: String? = null

    @Attribute(nullable = true)
    var longValue: Long? = null
    @Attribute
    var longPrimitive: Long = 0
    @Attribute
    var intValue: Int = 0
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

    /**
     * @see schemaupdate.TestAttributeUpdate
     * Un Comment for initializeTestWithBasicAttribute
     */
    //    @Attribute
    //    public String addedAttribute;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "parent", inverseClass = SchemaRelationshipEntity::class, cascadePolicy = CascadePolicy.SAVE)
            //    @Relationship(type = RelationshipType.MANY_TO_ONE, inverse = "parent", inverseClass = SchemaRelationshipEntity.class, cascadePolicy = CascadePolicy.SAVE)
    var child: SchemaRelationshipEntity? = null

}
