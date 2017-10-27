package entities.schema

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.RelationshipType

import java.util.Date

/**
 * Created by Tim Osborn on 8/23/15.
 */
@Entity
class SchemaRelationshipEntity : ManagedEntity(), IManagedEntity {
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

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "child", inverseClass = SchemaAttributeEntity::class)
    var parent: SchemaAttributeEntity? = null

    //    @Relationship(type = RelationshipType.ONE_TO_MANY, inverse = "child", inverseClass = SchemaAttributeEntity.class)
    //    public List<SchemaAttributeEntity> parent = null;
    /**
     * Un Comment for initializeTestWithBasicAttribute
     */
    @Attribute
    @Suppress("UNUSED")
    var addedAttribute: String? = null

    //    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = SchemaAttributeEntity.class)
    //    public SchemaAttributeEntity addedRelationship = null;
}
