package com.onyxdevtools.embedded.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

@Entity
public class Actor extends Person implements IManagedEntity
{

    @Identifier
    @Attribute
    public int actorId;

    @Attribute
    public String firstName;

    @Attribute
    public String lastName;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Movie.class,
            inverse = "actors")
    public Movie movie;
}
