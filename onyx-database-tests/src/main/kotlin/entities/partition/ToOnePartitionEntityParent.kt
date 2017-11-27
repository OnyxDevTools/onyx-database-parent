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
@Entity
class ToOnePartitionEntityParent : ManagedEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    @Partition
    var partitionId: Long? = null

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "parent", inverseClass = ToOnePartitionEntityChild::class, cascadePolicy = CascadePolicy.DELETE)
    var child: ToOnePartitionEntityChild? = null
}
