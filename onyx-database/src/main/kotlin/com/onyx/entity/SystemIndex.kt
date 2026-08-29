package com.onyx.entity

import com.onyx.descriptor.IndexDescriptor
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IndexType

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
    var indexType: IndexType = IndexType.DEFAULT,

    @Attribute
    var entropy: Int = 0,

    @Attribute
    var encodingVersion: Int = 0,

    @Attribute
    var configurationId: Long = 0L,

    @Attribute
    var configurationSignature: String = ""

): ManagedEntity() {

    constructor(descriptor: IndexDescriptor):this(
        name = descriptor.name,
        indexType = descriptor.indexType,
        entropy = descriptor.entropy,
        encodingVersion = descriptor.encodingVersion,
        configurationId = descriptor.configurationId,
        configurationSignature = descriptor.configurationSignature
    )
}
