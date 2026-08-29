package entities

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.Date

/**
 * Deterministic, deliberately wide scalar fixture for the opt-in vector query benchmark.
 *
 * The fields cover every scalar family accepted by the persistence layer. The benchmark inserts
 * ranks in shuffled order so index value order is independent of physical record order.
 */
@Entity(fileName = "vector-query-benchmark/", entropy = 128)
class VectorQueryBenchmarkEntity : VectorManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute
    var byteValue: Byte = 0

    @Attribute
    var shortValue: Short = 0

    @Attribute
    var intValue: Int = 0

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.INTERVAL]
    )
    var longValue: Long = 0L

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.CATEGORICAL, VectorFeatureFamily.INTERVAL]
    )
    var floatValue: Float = 0.0f

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.CATEGORICAL, VectorFeatureFamily.INTERVAL]
    )
    var doubleValue: Double = 0.0

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.INTERVAL, VectorFeatureFamily.TEXT_PREFIX]
    )
    var dateValue: Date = Date(0L)

    @Attribute
    var charValue: Char = '\u0000'

    @Attribute
    var booleanValue: Boolean = false

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.CATEGORICAL, VectorFeatureFamily.TEXT_EXACT]
    )
    var enumValue: VectorQueryBenchmarkState = VectorQueryBenchmarkState.ALPHA

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.CATEGORICAL, VectorFeatureFamily.INTERVAL]
    )
    var category: String = ""

    @Attribute
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM, VectorFeatureFamily.TEXT_NGRAM]
    )
    var body: String = ""

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.SELECTED)
    var nullableTag: String? = null
}

enum class VectorQueryBenchmarkState(private val displayName: String) {
    ALPHA("state alpha"),
    BRAVO("state bravo"),
    CHARLIE("state charlie"),
    DELTA("state delta"),
    ECHO("state echo"),
    FOXTROT("state foxtrot"),
    GOLF("state golf"),
    HOTEL("state hotel");

    override fun toString(): String = displayName
}
