package entities.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
class BasicPartitionEntity : ManagedEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Partition
    @Attribute
    var partitionId: Long? = null
}
