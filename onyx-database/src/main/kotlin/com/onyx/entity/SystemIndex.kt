package com.onyx.entity

import com.onyx.descriptor.IndexDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * Index information for entity
 */
@Entity(fileName = "system")
data class SystemIndex @JvmOverloads constructor(

    @Identifier(loadFactor = 5)
    var name: String = "",

    @Attribute
    var loadFactor: Int = 1

): ManagedEntity() {

    constructor(descriptor: IndexDescriptor):this(
        name = descriptor.name,
        loadFactor = descriptor.loadFactor.toInt()
    )
}
