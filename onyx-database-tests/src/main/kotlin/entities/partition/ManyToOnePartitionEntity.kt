package entities.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
class ManyToOnePartitionEntity : ManagedEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    @Partition
    var partitionId: Long? = null

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverse = "child", inverseClass = OneToManyPartitionEntity::class, fetchPolicy = FetchPolicy.EAGER)
    var parents: MutableList<OneToManyPartitionEntity>? = null
}
