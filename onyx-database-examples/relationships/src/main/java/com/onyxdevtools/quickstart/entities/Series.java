package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class Series extends ManagedEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String seriesId;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Season.class,
            inverse = "downloadableSeries")
    public List<Season> availableSeasons;


    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = Season.class,
            fetchPolicy = FetchPolicy.EAGER,
            inverse = "series")
    public List<Season> seasons;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
                  inverse = "series",
                  inverseClass = Episode.class)
    public Episode pilotEpisode;

}
