package entities.identifiers

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator
import entities.AbstractEntity

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
class ImmutableSequenceIdentifierEntity : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var identifier: Long = 0

    @Attribute
    var correlation: Int = 0

}
