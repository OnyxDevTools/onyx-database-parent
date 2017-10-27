package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 11/15/14.
 */
@Entity
class OneToManyChildFetchEntity : AbstractInheritedAttributes(), IManagedEntity {
    @Attribute
    @Identifier
    var id: String? = null

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = OneToOneFetchEntity::class, inverse = "children")
    var parents: OneToOneFetchEntity? = null

}
