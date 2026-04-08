package entities.delete

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition

/**
 * Partitioned entity for testing partition data file deletion
 */
@Entity(fileName = "test_partition_entity")
class TestPartitionEntity : ManagedEntity() {
    @Identifier
    var id: String? = null

    @Partition
    @Attribute
    var partitionId: String? = null

    @Attribute
    var data: String? = null
}