package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by tosborn1 on 3/27/16.
 */
@Entity
public class Season extends ManagedEntity implements IManagedEntity
{
    public Season()
    {

    }

    public Season(int seasonNumber, int seasonYear)
    {
        this.seasonNumber = seasonNumber;
        this.seasonYear = seasonYear;
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public long seasonId;

    @Attribute
    public int seasonNumber;

    @Attribute
    public int seasonYear;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Series.class,
            inverse = "availableSeasons")
    public Series downloadableSeries;


    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Series.class,
            inverse = "seasons")
    public Series series;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
                  cascadePolicy = CascadePolicy.SAVE,
                  inverse = "season",
                  inverseClass = Episode.class,
                  fetchPolicy = FetchPolicy.LAZY)
    public List<Episode> episodes;
}
