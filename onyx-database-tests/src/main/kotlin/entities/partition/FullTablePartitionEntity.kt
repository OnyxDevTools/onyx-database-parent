package entities.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by timothy.osborn on 3/5/15.
 */

@Entity(fileName = "web/partition")
class FullTablePartitionEntity : ManagedEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    @Partition
    var partitionId: Long? = null

    @Attribute
    var indexVal: Long = 0
}
