package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
class InheritedLongAttributeEntity : AbstractInheritedAttributes(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 1)
    @Attribute
    var id: Long = 0

}
