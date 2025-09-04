package dev.onyx.ai.transformation.impl

import dev.onyx.ai.transformation.ColumnTransform
import java.io.Serializable

/**
 * Normalizes a **single column** of boolean-like values (0.0 ↔ 1.0).
 *
 * * If [centered] is **false** (default) the column is returned unchanged except
 *   that every element is forced to exactly 0 or 1 if it was already one of
 *   those two values.
 * * If [centered] is **true** the mapping becomes:
 *
 * ```
 * 0.0  →  -1.0
 * 1.0  →   1.0
 * other →   other   (left untouched)
 * ```
 *
 * Centering is handy when your downstream layers / algorithms work best with
 * zero-mean inputs.
 *
 * @param centered Whether to map {0, 1} → {-1, 1}.
 */
class BooleanTransform(
    private val centered: Boolean = true
) : ColumnTransform, Serializable {

    /**
     * Transforms the supplied **column**.
     *
     * @param values A `FloatArray` representing one feature column.
     * @return A *new* array with the transform applied.
     */
    override fun apply(values: FloatArray): FloatArray =
        FloatArray(values.size) { idx ->
            when (val v = values[idx]) {
                0.0f, 1.0f ->
                    if (centered) (v - 0.5f) * 2           // 0→-1, 1→1
                    else v                                // keep 0/1 as is
                else -> v                                 // non-boolean values untouched
            }
        }

    /**
     * Restores centered values back to {0.0, 1.0}.
     * If the transform was not centered, the input is returned unchanged.
     */
    override fun inverse(values: FloatArray): FloatArray =
        if (!centered) values
        else FloatArray(values.size) { idx ->
            when (val v = values[idx]) {
                -1.0f, 1.0f -> (v + 1) / 2                  // -1→0, 1→1
                else -> v
            }
        }

    /**
     * Clone the transform so that if the state is mutated during stream fitting,
     * it does not impact the best model.
     *
     * @return An identical deep copy of the ColumnTransform.
     */
    override fun clone(): ColumnTransform = BooleanTransform(centered)
}
