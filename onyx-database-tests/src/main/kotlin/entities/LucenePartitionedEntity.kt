package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.EntityType
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity(type = EntityType.DOCUMENT)
class LucenePartitionedEntity : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long = 0

    @Partition
    @Attribute
    var region: String? = null

    @Attribute
    var tag: String? = null

    @Attribute
    var body: String? = null
}
