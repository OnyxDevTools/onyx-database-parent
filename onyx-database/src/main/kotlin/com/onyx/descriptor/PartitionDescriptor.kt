package com.onyx.descriptor

import com.onyx.extension.common.ClassMetadata

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Detail regarding an entity partition
 */
data class PartitionDescriptor(
    var partitionValue:String = "",
    var name: String = "",
    var type: Class<*> = ClassMetadata.ANY_CLASS
) : AbstractBaseDescriptor() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PartitionDescriptor) return false

        // Use identity comparison for Class since they're singletons per classloader
        if (partitionValue != other.partitionValue) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = partitionValue.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
