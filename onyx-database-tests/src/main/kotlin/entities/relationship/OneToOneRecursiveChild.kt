package entities.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.RelationshipType
import entities.AbstractEntity

/**
 * Created by timothy.osborn on 1/26/15.
 */
@Entity
class OneToOneRecursiveChild : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var id: Long = 0

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneRecursive::class, inverse = "child", cascadePolicy = CascadePolicy.SAVE)
    var parent: OneToOneRecursive? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneThreeDeep::class, inverse = "parent", cascadePolicy = CascadePolicy.SAVE)
    var third: OneToOneThreeDeep? = null

}
