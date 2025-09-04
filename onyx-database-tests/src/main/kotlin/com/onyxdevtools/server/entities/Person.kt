package dev.onyx.server.entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Tim Osborn on 10/20/15.
 */
@Entity
open class Person : ManagedEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Suppress("unused")
    var personId: Long = 0

    @Attribute
    var firstName: String? = null

    @Attribute
    var lastName: String? = null
}
