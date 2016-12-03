package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class Episode extends ManagedEntity implements IManagedEntity
{
    public Episode()
    {

    }

    public Episode(String episodeId, int episodeNumber)
    {
        this.episodeId = episodeId;
        this.episodeNumber = episodeNumber;
    }

    @Attribute
    @Identifier
    public String episodeId;

    @Attribute
    public int episodeNumber;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverse = "episodes",
            inverseClass = Season.class)
    public Season season;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            inverse = "pilotEpisode",
            inverseClass = Series.class)
    public Series series;


    @Relationship(type = RelationshipType.ONE_TO_MANY,
                  inverseClass = Actor.class,
                  cascadePolicy = CascadePolicy.SAVE,
                  fetchPolicy = FetchPolicy.NONE)
    public List<Actor> actors;

}
