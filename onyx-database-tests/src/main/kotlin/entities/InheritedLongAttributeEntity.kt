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
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InheritedLongAttributeEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = id.hashCode()


}
