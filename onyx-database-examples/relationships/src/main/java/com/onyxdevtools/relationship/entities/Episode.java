package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

@Entity
@SuppressWarnings("unused")
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
    private String episodeId;

    @Attribute
    private int episodeNumber;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverse = "episodes",
            inverseClass = Season.class)
    private Season season;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            inverse = "pilotEpisode",
            inverseClass = Series.class)
    private Series series;


    @Relationship(type = RelationshipType.ONE_TO_MANY,
                  inverseClass = Actor.class,
                  cascadePolicy = CascadePolicy.SAVE,
                  fetchPolicy = FetchPolicy.NONE)
    private List<Actor> actors;

    public String getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(String episodeId) {
        this.episodeId = episodeId;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public Season getSeason() {
        return season;
    }

    public void setSeason(Season season) {
        this.season = season;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }

    public List<Actor> getActors() {
        return actors;
    }

    public void setActors(List<Actor> actors) {
        this.actors = actors;
    }
}
