package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 11/15/14.
 */
@Entity
class OneToOneChildFetchEntity : AbstractInheritedAttributes(), IManagedEntity {
    @Attribute
    @Identifier
    var id: String? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneFetchEntity::class, inverse = "child")
    var parent: OneToOneFetchEntity? = null

}
