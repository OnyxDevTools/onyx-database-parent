package entities.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import entities.AbstractEntity

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
class OneToManyParent : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: String? = null

    @Attribute
    var correlation: Int = 0


    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.NONE, inverseClass = OneToManyChild::class, inverse = "parentNoCascade")
    var childNoCascade: MutableList<OneToManyChild>? = null


    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.ALL, inverseClass = OneToManyChild::class, fetchPolicy = FetchPolicy.EAGER, inverse = "parentCascade")
    var childCascade: MutableList<OneToManyChild>? = null

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.ALL, inverseClass = OneToManyChild::class)
    var childNoInverseCascade: MutableList<OneToManyChild>? = null

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.NONE, inverseClass = OneToManyChild::class)
    var childNoInverseNoCascade: MutableList<OneToManyChild>? = null

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.NONE, inverseClass = OneToManyChild::class, fetchPolicy = FetchPolicy.EAGER, inverse = "parentCascadeTwo")
    var childCascadeTwo: MutableList<OneToManyChild>? = null
}
