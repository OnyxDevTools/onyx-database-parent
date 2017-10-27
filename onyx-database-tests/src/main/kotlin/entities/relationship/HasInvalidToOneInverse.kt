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
class HasInvalidToOneInverse : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: String? = null

    @Attribute
    var correlation: Int = 0

    @Relationship(type = RelationshipType.ONE_TO_ONE, cascadePolicy = CascadePolicy.SAVE, inverseClass = HasInvalidToOne::class, inverse = "parent")
    var child: MutableList<HasInvalidToOne>? = null

}
