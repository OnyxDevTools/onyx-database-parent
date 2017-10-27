package entities.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
class OneToManyPartitionEntity : ManagedEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    @Partition
    var partitionId: Long? = null

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverse = "parents", inverseClass = ManyToOnePartitionEntity::class, cascadePolicy = CascadePolicy.SAVE)
    var child: ManyToOnePartitionEntity? = null
}
