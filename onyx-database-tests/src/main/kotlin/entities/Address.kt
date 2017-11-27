package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Entity
class Address : ManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute(nullable = false)
    var street: String? = null

    @Attribute(nullable = false)
    var houseNr: Int = 0

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = Person::class, inverse = "address", cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.LAZY)
    @Suppress("UNUSED")
    var occupants: MutableList<Person>? = null
}
