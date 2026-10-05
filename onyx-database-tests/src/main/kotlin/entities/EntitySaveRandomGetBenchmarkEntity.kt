package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Stable, explicitly identified record used by the end-to-end save/read benchmark.
 *
 * The fields deliberately mix numeric values with repeated strings so the benchmark exercises
 * ordinary entity serialization while giving block compression realistic repeated content.
 */
@Entity(fileName = "sample-database-benchmark")
class EntitySaveRandomGetBenchmarkEntity : ManagedEntity() {

    @Attribute(nullable = false)
    @Identifier(generator = IdentifierGenerator.NONE)
    var id: Long = 0L

    @Attribute(nullable = false)
    var tenantId: Int = 0

    @Attribute(nullable = false)
    var sequence: Long = 0L

    @Attribute(nullable = false)
    var timestampEpochMillis: Long = 0L

    @Attribute(nullable = false)
    var amount: Double = 0.0

    @Attribute(nullable = false)
    var active: Boolean = false

    @Attribute(nullable = false, size = 32)
    var status: String = ""

    @Attribute(nullable = false, size = 256)
    var payload: String = ""
}
