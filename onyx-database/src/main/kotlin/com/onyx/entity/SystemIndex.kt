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

    @Identifier
    var name: String = ""

): ManagedEntity() {

    constructor(descriptor: IndexDescriptor):this(
        name = descriptor.name
    )
}
