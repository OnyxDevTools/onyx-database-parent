package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.annotations.*;
import java.util.List;

@Entity
public class Movie
{
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected long movieId;

    @Attribute(size = 255, nullable = false)
    protected String title;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
            inverse = "movies",
            inverseClass = Actor.class,
            fetchPolicy = FetchPolicy.EAGER,
            cascadePolicy = CascadePolicy.SAVE)
    protected List<Actor> actors;

    public long getMovieId() {
        return movieId;
    }

    public void setMovieId(long movieId) {
        this.movieId = movieId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Actor> getActors() {
        return actors;
    }

    public void setActors(List<Actor> actors) {
        this.actors = actors;
    }
}
