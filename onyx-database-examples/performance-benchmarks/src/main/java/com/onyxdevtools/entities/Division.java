package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import javax.persistence.CascadeType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.List;

/**
 * @author Chris Osborn
 */
//J-
@Entity
@javax.persistence.Entity
class Division extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Id
    @Identifier
    private String divisionName;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Team.class,
            cascadePolicy = CascadePolicy.ALL,
            inverse = "division"
    )
    @OneToMany(cascade = CascadeType.ALL, targetEntity = Team.class, mappedBy = "division")
    private List<Team> teams;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "divisions",
            inverseClass = Conference.class
    )
    @ManyToOne(targetEntity = Conference.class)
    private Conference conference;

    public Division()
    {
    }

    @SuppressWarnings("unused")
    public String getDivisionName()
    {
        return divisionName;
    }

    @SuppressWarnings("unused")
    public void setDivisionName(String divisionName)
    {
        this.divisionName = divisionName;
    }

    @SuppressWarnings("unused")
    public List<Team> getTeams()
    {
        return teams;
    }

    @SuppressWarnings("unused")
    public void setTeams(List<Team> teams)
    {
        this.teams = teams;
    }

    @SuppressWarnings("unused")
    public void setConference(Conference conference)
    {
        this.conference = conference;
    }

    @SuppressWarnings("unused")
    public Conference getConference()
    {
        return this.conference;
    }
}
