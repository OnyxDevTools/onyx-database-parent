package entities.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import entities.AbstractEntity

/**
 * Created by timothy.osborn on 11/5/14.
 */
@Entity
class ManyToManyParent : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: String? = null

    @Attribute
    var correlation: Int = 0

    @Relationship(type = RelationshipType.MANY_TO_MANY, cascadePolicy = CascadePolicy.NONE, fetchPolicy = FetchPolicy.EAGER, inverse = "parentNoCascade", inverseClass = ManyToManyChild::class)
    var childNoCascade: MutableList<ManyToManyChild>? = null

    @Relationship(type = RelationshipType.MANY_TO_MANY, cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.LAZY, inverseClass = ManyToManyChild::class, inverse = "parentCascade")
    var childCascade: MutableList<ManyToManyChild>? = null

    @Relationship(type = RelationshipType.MANY_TO_MANY, cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.LAZY, inverseClass = ManyToManyChild::class)
    var childNoInverseCascade: MutableList<ManyToManyChild>? = null


    @Relationship(type = RelationshipType.MANY_TO_MANY, cascadePolicy = CascadePolicy.SAVE, fetchPolicy = FetchPolicy.LAZY, inverseClass = ManyToManyChild::class, inverse = "parentCascade")
    var childCascadeSave: MutableList<ManyToManyChild>? = null

}
