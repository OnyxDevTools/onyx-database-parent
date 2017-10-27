package com.onyxdevtools.server.entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

@Entity
class Movie : ManagedEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var movieId: Int = 0

    @Attribute
    var title: String? = null

    @Relationship(type = RelationshipType.ONE_TO_MANY, cascadePolicy = CascadePolicy.DEFER_SAVE, inverse = "movie", inverseClass = Actor::class)
    var actors: List<Actor>? = null
}