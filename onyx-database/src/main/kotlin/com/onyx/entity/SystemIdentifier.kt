package com.onyx.entity

import com.onyx.descriptor.IdentifierDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*

/**
 * Created by timothy.osborn on 3/2/15.
 *
 * System entity for entity identifier
 */
@Entity(fileName = "system")
data class SystemIdentifier @JvmOverloads constructor(

    @Identifier
    @Attribute
    var name: String = "",

    @Attribute
    var generator: Byte = 0,

    @Attribute
    var loadFactor: Byte = 0

) : ManagedEntity() {

    constructor(descriptor: IdentifierDescriptor) : this(
        name  = descriptor.name,
        loadFactor = descriptor.loadFactor,
        generator = descriptor.generator.ordinal.toByte()
    )

}
