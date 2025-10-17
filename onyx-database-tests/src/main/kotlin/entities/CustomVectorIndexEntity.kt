package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.IndexType

/**
 * Example entity demonstrating the use of VECTOR index type with custom properties
 */
@Entity
class CustomVectorIndexEntity : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long = 0

    @Index(
        type = IndexType.VECTOR,
        embeddingDimensions = 256,
        minimumScore = 0.25f,
        hashTableCount = 8
    )
    @Attribute
    var customVectorData: String? = null

    @Attribute
    var label: String? = null
}
