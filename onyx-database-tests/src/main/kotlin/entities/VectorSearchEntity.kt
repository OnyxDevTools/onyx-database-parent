package entities

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity(fileName = "vector-search/", entropy = 64)
class VectorSearchEntity : VectorManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var id: Long = 0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var title: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var body: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var category: String? = null
}
