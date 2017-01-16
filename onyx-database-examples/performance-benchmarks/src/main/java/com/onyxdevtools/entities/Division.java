package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import javax.persistence.CascadeType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.List;

/**
 * @author cosborn
 */
//J-
@Entity
@javax.persistence.Entity
public class Division extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Id
    @Identifier(loadFactor = 1)
    protected String divisionName;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Team.class,
            cascadePolicy = CascadePolicy.ALL,
            inverse = "division",
            loadFactor = 1
    )
    @OneToMany(cascade = CascadeType.ALL, targetEntity = Team.class, mappedBy = "division")
    protected List<Team> teams;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "divisions",
            inverseClass = Conference.class,
            loadFactor = 1
    )
    @ManyToOne(targetEntity = Conference.class)
    protected Conference conference;

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

}
