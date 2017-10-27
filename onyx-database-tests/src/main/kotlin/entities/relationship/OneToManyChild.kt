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
class OneToManyChild : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: String? = null

    @Attribute
    var correlation: Int = 0


    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverseClass = OneToManyParent::class, inverse = "childNoCascade")
    var parentNoCascade: OneToManyParent? = null

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverseClass = OneToManyParent::class, inverse = "childCascade")
    var parentCascade: OneToManyParent? = null

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.ALL, inverseClass = OneToManyParent::class, inverse = "childCascadeTwo")
    var parentCascadeTwo: OneToManyParent? = null
    /*
    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyParent.class,
            inverse = "childNoInverseCascade")
    public OneToManyParent parentNoInverseCascade;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyParent.class,
            inverse = "childNoInverseNoCascade")
    public OneToManyParent parentNoInverseNoCascade;
    */
}
