package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.RelationshipType
import entities.AllAttributeEntity

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
class EntityToOneIdDoesNotMatch : ManagedEntity(), IManagedEntity {
    @Identifier
    @Attribute
    var id = "234"

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = AllAttributeEntity::class)
    var relationshipId: Long? = null
}
