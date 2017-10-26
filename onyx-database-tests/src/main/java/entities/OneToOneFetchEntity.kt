package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 11/15/14.
 */
@Entity
class OneToOneFetchEntity : AbstractInheritedAttributes(), IManagedEntity {
    @Attribute
    @Identifier
    var id: String? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneChildFetchEntity::class, inverse = "parent")
    var child: OneToOneChildFetchEntity? = null

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = OneToManyChildFetchEntity::class, inverse = "parents")
    var children: MutableList<OneToManyChildFetchEntity>? = null
}
