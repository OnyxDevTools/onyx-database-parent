package dev.onyx.server.entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.RelationshipType

@Entity
class Actor : Person(), IManagedEntity {

    @Identifier
    var actorId: Int = 0

    @Relationship(type = RelationshipType.MANY_TO_ONE, cascadePolicy = CascadePolicy.NONE, inverseClass = Movie::class, inverse = "actors")
    @Suppress("unused")
    var movie: Movie? = null
}
