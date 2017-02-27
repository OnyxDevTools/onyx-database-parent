package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

@SuppressWarnings("unused")
@Entity
public class Actor extends Person implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private int actorId;

    @Attribute
    private String firstName;

    @Attribute
    private String lastName;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
                  cascadePolicy = CascadePolicy.NONE,
                  inverseClass = Movie.class,
                  inverse = "actors")
    private List<Movie> movies;

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }

    public int getActorId() {
        return actorId;
    }

    public void setActorId(int actorId) {
        this.actorId = actorId;
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
