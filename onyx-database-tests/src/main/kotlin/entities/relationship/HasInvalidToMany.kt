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
class HasInvalidToMany : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: String? = null

    @Attribute
    var correlation: Int = 0

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.SAVE, inverseClass = OneToOneChild::class)
    var child: OneToOneChild? = null

}
