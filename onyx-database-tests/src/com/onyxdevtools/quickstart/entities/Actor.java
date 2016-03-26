package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.annotations.*;

import java.util.List;

@Entity
public class Actor extends Person
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected long actorId;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
            inverse = "actors",
            inverseClass = Movie.class,
            fetchPolicy = FetchPolicy.LAZY,
            cascadePolicy = CascadePolicy.NONE)
    protected List<Movie> movies;

    public long getActorId() {
        return actorId;
    }

    public void setActorId(long actorId) {
        this.actorId = actorId;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }
}
