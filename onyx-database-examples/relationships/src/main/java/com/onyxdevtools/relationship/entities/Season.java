package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

@Entity
@SuppressWarnings("unused")
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
    private long seasonId;

    @Attribute
    private int seasonNumber;

    @Attribute
    private int seasonYear;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Series.class,
            inverse = "availableSeasons")
    private Series downloadableSeries;


    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = Series.class,
            inverse = "seasons")
    private Series series;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
                  cascadePolicy = CascadePolicy.SAVE,
                  inverse = "season",
                  inverseClass = Episode.class,
                  fetchPolicy = FetchPolicy.LAZY)
    private List<Episode> episodes;

    public long getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(long seasonId) {
        this.seasonId = seasonId;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public void setSeasonYear(int seasonYear) {
        this.seasonYear = seasonYear;
    }

    public Series getDownloadableSeries() {
        return downloadableSeries;
    }

    public void setDownloadableSeries(Series downloadableSeries) {
        this.downloadableSeries = downloadableSeries;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(List<Episode> episodes) {
        this.episodes = episodes;
    }
}
