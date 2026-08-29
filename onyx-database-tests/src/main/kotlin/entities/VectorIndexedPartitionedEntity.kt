package entities

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity(fileName = "vector-indexed/")
class VectorIndexedPartitionedEntity : VectorManagedEntity() {

    @Identifier(generator = IdentifierGenerator.UUID)
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var id: String = ""

    @Partition
    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var region: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var tag: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var body: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var value: String? = null

    @Index
    var databaseId: String? = null
}
