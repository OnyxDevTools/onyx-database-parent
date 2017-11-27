package com.onyx.descriptor

import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * General information about a key identifier for an entity
 */
data class IdentifierDescriptor(
        var generator: IdentifierGenerator = IdentifierGenerator.NONE,
        override var name: String = "",
        override var type: Class<*> = Any::class.java,
        override var loadFactor: Byte = 1
) : IndexDescriptor(),BaseDescriptor {

    override lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int = super.hashCode() + generator.hashCode()
    override fun equals(other: Any?): Boolean = super.equals(other) && (other as IdentifierDescriptor).generator == generator
}
