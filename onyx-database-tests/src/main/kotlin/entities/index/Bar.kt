package entities.index

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.Date

/**
 * Benchmark representation of the Bar schema.
 *
 * Resolvers and triggers are intentionally omitted because this entity is used
 * to measure the persistent BTree footprint of its records and indexes.
 */
@Entity(fileName = "bar")
class Bar : ManagedEntity() {

    @Attribute(nullable = true)
    @Identifier(generator = IdentifierGenerator.NONE)
    var id: String? = null

    @Attribute(nullable = true)
    var close: Double? = null

    @Attribute(nullable = true)
    var high: Double? = null

    @Attribute(nullable = true)
    var low: Double? = null

    @Attribute(nullable = true)
    var open: Double? = null

    @Attribute(nullable = true)
    @Index
    var symbol: String? = null

    @Attribute(nullable = true)
    @Index
    var timestamp: Date? = null

    @Attribute(nullable = true)
    @Partition
    var underlying: String? = null

    @Attribute(nullable = true)
    var volume: Int? = null

    @Attribute(nullable = true)
    var interval: String? = null

    @Attribute(nullable = true)
    var volumeWeightedPrice: Double? = null

    @Attribute(nullable = true)
    @Index
    var sequence: Long? = null
}
