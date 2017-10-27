package entities.schema

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Tim Osborn on 9/1/15.
 */
@Entity
class SchemaIndexChangedEntity : ManagedEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long = 0

    @Attribute(nullable = true)
    @Index
    var longValue: String? = null

    @Attribute(nullable = true)
    @Index
    var otherIndex: Int = 0

}