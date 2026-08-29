package entities

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.Date

@Entity(fileName = "vector-data-types/")
class VectorDataTypeEntity : VectorManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var id: Long = 0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var title: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var description: String? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var longValue: Long? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var intValue: Int? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var doubleValue: Double? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var floatValue: Float? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var booleanValue: Boolean? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var dateValue: Date? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var byteValue: Byte? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var shortValue: Short? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var charValue: Char? = null
}
