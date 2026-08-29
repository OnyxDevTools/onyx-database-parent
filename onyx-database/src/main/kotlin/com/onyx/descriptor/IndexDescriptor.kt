package com.onyx.descriptor

import com.onyx.extension.common.ClassMetadata
import com.onyx.persistence.annotations.values.IndexType

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * General information regarding an index within an entity
 */
open class IndexDescriptor(
    override var name: String = "",
    open var type: Class<*> = ClassMetadata.ANY_CLASS,
    open var indexType: IndexType = IndexType.DEFAULT,
    open var entropy: Int = 0,
    open var encodingVersion: Int = 0,
    open var configurationId: Long = 0L,
    open var configurationSignature: String = ""
) : AbstractBaseDescriptor(), BaseDescriptor {

    open lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int {
        var result = (((System.identityHashCode(this.entityDescriptor.entityClass)) * 31) * 31 + this.name.hashCode()) * 31
        result = 31 * result + entityDescriptor.partition.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexDescriptor) return false

        // Compare partition using equals (which now uses identity for Class)
        if (this.entityDescriptor.partition != other.entityDescriptor.partition) return false
        if (this.name != other.name) return false

        return true
    }
}
