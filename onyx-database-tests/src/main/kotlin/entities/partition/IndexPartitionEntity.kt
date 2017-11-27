package entities.partition

/**
 * Created by timothy.osborn on 3/5/15.
 */

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity(fileName = "web/partition")
class IndexPartitionEntity : ManagedEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    @Partition
    var partitionId: Long? = null

    @Index
    @Attribute
    var indexVal: Long = 0
}
