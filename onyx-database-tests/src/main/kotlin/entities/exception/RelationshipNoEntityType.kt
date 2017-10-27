package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
class RelationshipNoEntityType : IManagedEntity {
    @Identifier
    @Attribute
    var id = "234"

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = Any::class)
    var relationship: Any? = null
}
