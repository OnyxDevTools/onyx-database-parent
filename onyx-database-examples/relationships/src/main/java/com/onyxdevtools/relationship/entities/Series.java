package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

@Entity
@SuppressWarnings("unused")
public class Series extends ManagedEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    private String seriesId;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Season.class,
            inverse = "downloadableSeries")
    private List<Season> availableSeasons;


    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = Season.class,
            fetchPolicy = FetchPolicy.EAGER,
            inverse = "series")
    private List<Season> seasons;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
                  inverse = "series",
                  inverseClass = Episode.class)
    private Episode pilotEpisode;

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public List<Season> getAvailableSeasons() {
        return availableSeasons;
    }

    public void setAvailableSeasons(List<Season> availableSeasons) {
        this.availableSeasons = availableSeasons;
    }

    public List<Season> getSeasons() {
        return seasons;
    }

    public void setSeasons(List<Season> seasons) {
        this.seasons = seasons;
    }

    public Episode getPilotEpisode() {
        return pilotEpisode;
    }

    public void setPilotEpisode(Episode pilotEpisode) {
        this.pilotEpisode = pilotEpisode;
    }
}
