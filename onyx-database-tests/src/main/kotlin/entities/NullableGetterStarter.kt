package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

@Entity(fileName = "nullableGetterStarter.dat")
data class NullableGetterStarter(
    @Identifier
    @Attribute
    var starterId: String = "",
    @Attribute
    var medication: String? = null,
    @Attribute
    var hasPerformance: Boolean = false,
    @Attribute
    var finishPosition: Int? = null,
) : ManagedEntity() {

    val performance: NullableGetterPerformance?
        get() = if (hasPerformance) {
            NullableGetterPerformance(
                starterId = starterId,
                finishPosition = finishPosition,
            )
        } else {
            null
        }
}

@Entity(fileName = "nullableGetterPerformance.dat")
data class NullableGetterPerformance(
    @Identifier
    @Attribute
    var starterId: String = "",
    @Attribute
    var finishPosition: Int? = null,
) : ManagedEntity()
