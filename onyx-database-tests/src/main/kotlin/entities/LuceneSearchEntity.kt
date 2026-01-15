package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.EntityType
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity(type = EntityType.DOCUMENT)
class LuceneSearchEntity : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long = 0

    @Attribute
    var title: String? = null

    @Attribute
    var body: String? = null

    @Attribute
    var category: String? = null
}
