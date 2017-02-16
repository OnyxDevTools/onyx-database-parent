package com.onyxdevtools.server.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

@Entity
public class Movie extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public int movieId;

    @Attribute
    public String title;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.DEFER_SAVE,
            inverse = "movie",
            inverseClass = Actor.class)
    public List<Actor> actors;
}