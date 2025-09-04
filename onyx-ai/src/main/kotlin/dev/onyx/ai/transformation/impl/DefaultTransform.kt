package dev.onyx.ai.transformation.impl

import dev.onyx.ai.transformation.ColumnTransform
import java.io.Serializable

/**
 * An identity transformation that applies no changes to the input [FloatArray].
 * Both the forward (`apply`) and inverse (`inverse`) operations return the matrix unmodified.
 *
 * This can be useful as a placeholder, a default transformation when none is specified,
 * or in conditional logic where sometimes no transformation is needed.
 *
 * Implements [Serializable] to allow the transformation state (although none exists) to be saved.
 */
class DefaultTransform: ColumnTransform, Serializable {
    override fun clone(): ColumnTransform = DefaultTransform()
}
