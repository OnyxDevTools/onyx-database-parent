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
) : AbstractBaseDescriptor()
