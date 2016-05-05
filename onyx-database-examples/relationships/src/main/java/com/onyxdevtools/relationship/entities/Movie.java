package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by tosborn1 on 3/26/16.
 */
@Entity
public class Movie extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public int movieId;

    @Attribute
    public String title;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
                  cascadePolicy = CascadePolicy.DEFER_SAVE,
                  inverse = "movie",
                  inverseClass = Actor.class)
    public List<Actor> actors;

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
