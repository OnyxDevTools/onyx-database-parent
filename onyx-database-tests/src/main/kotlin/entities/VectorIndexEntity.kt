package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.IndexType

/**
 * Example entity demonstrating the use of VECTOR index type
 */
@Entity
class VectorIndexEntity : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long = 0

    @Partition
    @Attribute
    var partitionId: Long? = null

    @Index(type = IndexType.LUCENE)
    @Attribute
    var vectorData: String? = null

    @Index(type = IndexType.LUCENE)
    @Attribute
    var vectorData2: String? = null

    @Attribute
    var label: String? = null
}
