package com.onyx.entity

import com.onyx.descriptor.IndexDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Index information for entity
 */
@Entity(fileName = "system")
data class SystemIndex @JvmOverloads constructor(

    @Identifier
    var name: String = "",

    @Attribute
    var type: String = "",

    @Attribute
    var loadFactor: Int = 0

): ManagedEntity() {

    constructor(descriptor: IndexDescriptor):this(
        name = descriptor.name,
        type = descriptor.type.canonicalName,
        loadFactor = descriptor.loadFactor.toInt()
    )
}
