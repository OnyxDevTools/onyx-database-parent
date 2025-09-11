package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity
data class EmbeddedTestEntity(
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long = 0,
    @Attribute
    var embeddedAttribute: Map<String, Any?> = emptyMap()
) : ManagedEntity()
