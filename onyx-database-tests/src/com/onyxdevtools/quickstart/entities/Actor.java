package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

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
