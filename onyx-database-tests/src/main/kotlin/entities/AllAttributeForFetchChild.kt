package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 1/13/15.
 */
@Entity
class AllAttributeForFetchChild : AbstractEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    var someOtherField: String? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = AllAttributeForFetch::class, inverse = "child")
    var parent: AllAttributeForFetch? = null

}
