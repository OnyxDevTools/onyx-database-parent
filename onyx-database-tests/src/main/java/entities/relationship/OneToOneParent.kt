package entities.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.RelationshipType
import entities.AbstractEntity

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
class OneToOneParent : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: String? = null

    @Attribute
    var correlation: Int = 0

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.SAVE, inverseClass = OneToOneChild::class, inverse = "parent")
    var child: OneToOneChild? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverseClass = OneToOneChild::class, inverse = "cascadeParent")
    var cascadeChild: OneToOneChild? = null


    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverseClass = OneToOneChild::class)
    var childNoInverseCascade: OneToOneChild? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.SAVE, inverseClass = OneToOneChild::class)
    var childNoInverse: OneToOneChild? = null
}
