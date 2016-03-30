package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by tosborn1 on 3/26/16.
 */
@Entity
public class Actor extends Person implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public int actorId;

    @Attribute
    public String firstName;

    @Attribute
    public String lastName;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
                  cascadePolicy = CascadePolicy.NONE,
                  inverseClass = Movie.class,
                  inverse = "actors")
    public List<Movie> movies;

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }
}
