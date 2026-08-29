package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.PreInsert
import com.onyx.persistence.annotations.PreUpdate
import com.onyx.persistence.annotations.Partition

/** Test entity whose callback widens the historical scan/write race deterministically. */
@Entity
class ConditionalUpdateEntity : AbstractEntity(), IManagedEntity {
    @Identifier
    @Attribute
    var id: String = ""

    @Partition
    @Attribute
    var region: String = ""

    @Index
    @Attribute
    var owner: String = ""

    @Attribute
    var generation: Long = 0L

    @PreInsert
    fun widenCreateIfAbsentRace() {
        Thread.sleep(25L)
    }

    @PreUpdate
    fun widenConditionalUpdateRace() {
        Thread.sleep(25L)
    }
}
