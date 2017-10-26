package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Entity
class Person : ManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Partition
    @Attribute
    var partitionVal = "ASDF"

    @Attribute(nullable = false)
    var firstName: String? = null

    @Attribute(nullable = false)
    var lastName: String? = null

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = Address::class, inverse = "occupants", cascadePolicy = CascadePolicy.ALL)
    var address: Address? = null

}
