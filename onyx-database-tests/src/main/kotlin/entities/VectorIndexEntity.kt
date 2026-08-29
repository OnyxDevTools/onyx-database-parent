package entities

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.values.IdentifierGenerator

/** Example whose persisted fields are routed by the inherited vector index. */
@Entity
class VectorIndexEntity : VectorManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var id: Long = 0

    @Partition
    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var partitionId: Long? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var vectorData: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var vectorData2: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var label: String? = null
}
