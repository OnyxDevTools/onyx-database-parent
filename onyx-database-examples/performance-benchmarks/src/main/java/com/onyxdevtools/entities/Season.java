package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

//J-
@com.onyx.persistence.annotations.Entity
@javax.persistence.Entity
public class Season extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    @Id
    @Column
    private Integer seasonYear;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Conference.class,
            inverse = "league",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY
    )
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Conference> conferences = new ArrayList<>();

    public Season()
    {
    }

    @SuppressWarnings("unused")
    public List<Conference> getConferences()
    {
        return conferences;
    }

    @SuppressWarnings("unused")
    public void setConferences(List<Conference> conferences)
    {
        this.conferences = conferences;
    }

    @SuppressWarnings("unused")
    public int getSeasonYear()
    {
        return seasonYear;
    }

    @SuppressWarnings("unused")
    public void setSeasonYear(int seasonYear)
    {
        this.seasonYear = seasonYear;
    }

}
