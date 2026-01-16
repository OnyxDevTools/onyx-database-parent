package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.EntityType
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.Date

@Entity(type = EntityType.DOCUMENT)
class LuceneDataTypeEntity : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long = 0

    @Attribute
    var title: String? = null

    @Attribute
    var description: String? = null

    @Attribute
    var longValue: Long? = null

    @Attribute
    var intValue: Int? = null

    @Attribute
    var doubleValue: Double? = null

    @Attribute
    var floatValue: Float? = null

    @Attribute
    var booleanValue: Boolean? = null

    @Attribute
    var dateValue: Date? = null

    @Attribute
    var byteValue: Byte? = null

    @Attribute
    var shortValue: Short? = null

    @Attribute
    var charValue: Char? = null
}
