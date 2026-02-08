package com.onyx.descriptor

import com.onyx.extension.common.ClassMetadata
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.annotations.values.VectorQuantization

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * General information regarding an index within an entity
 */
open class IndexDescriptor(
    override var name: String = "",
    open var type: Class<*> = ClassMetadata.ANY_CLASS,
    open var indexType: IndexType = IndexType.DEFAULT,
    open var embeddingDimensions: Int = -1,
    open var minimumScore: Float = -1f,
    open var maxNeighbors: Int = 16,
    open var searchRadius: Int = 128,
    open var quantization: VectorQuantization = VectorQuantization.NONE
) : AbstractBaseDescriptor(), BaseDescriptor {

    open lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int = (((System.identityHashCode(this.entityDescriptor.entityClass)) * 31) * 31 + this.name.hashCode()) * 31 + System.identityHashCode(this.type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexDescriptor) return false

        // Compare partition using equals (which now uses identity for Class)
        if (this.entityDescriptor.partition != other.entityDescriptor.partition) return false
        if (this.name != other.name) return false
        // Use identity comparison for Class since they're singletons per classloader
        if (this.type !== other.type) return false

        return true
    }
}
