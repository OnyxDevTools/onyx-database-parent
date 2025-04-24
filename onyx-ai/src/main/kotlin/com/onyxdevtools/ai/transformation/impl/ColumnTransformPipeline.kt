package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable

/**
 * Applies several [`ColumnTransform`]s to the **same column** in sequence.
 *
 * * `fit`  runs each transform in order, passing the output of the previous
 *           stage into the next stage's `fit`.
 * * `apply` runs the same forward sequence at inference time.
 * * `inverse` runs the stages in **reverse** so the column can be restored to
 *             its original scale.
 *
 * Example
 * ```kotlin
 * val pipeline = ColumnTransformPipeline(
 *     listOf(LogTransform(), StandardizeTransform())
 * )
 * ```
 */
class ColumnTransformPipeline(
    private val transforms: List<ColumnTransform>
) : ColumnTransform, Serializable {

    override fun fit(values: DoubleArray) {
        var data = values
        transforms.forEach { t ->
            t.fit(data)
            data = t.apply(data)           // propagate to next stage
        }
    }

    override fun apply(values: DoubleArray): DoubleArray {
        var out = values
        transforms.forEach { t -> out = t.apply(out) }
        return out
    }

    override fun inverse(values: DoubleArray): DoubleArray {
        var out = values
        transforms.asReversed().forEach { t -> out = t.inverse(out) }
        return out
    }

    /**
     * Clone the entire pipeline and all its sub-transforms so that
     * mutating one pipeline cannot affect another.
     *
     * @return A deep copy of this ColumnTransformPipeline.
     */
    override fun clone(): ColumnTransform = ColumnTransformPipeline(
        transforms.map { it.clone() }
    )
}
