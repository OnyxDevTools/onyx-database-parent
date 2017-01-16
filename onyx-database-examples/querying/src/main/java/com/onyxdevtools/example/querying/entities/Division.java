package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * @author cosborn
 */
//J-
@Entity
public class Division extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    protected String name;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Team.class,
            cascadePolicy = CascadePolicy.ALL,
            inverse = "division"
    )
    protected List<Team> teams;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "divisions",
            inverseClass = Conference.class
    )
    protected Conference conference;

    public Division()
    {
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public List<Team> getTeams()
    {
        return teams;
    }

    public void setTeams(List<Team> teams)
    {
        this.teams = teams;
    }

    public void setConference(Conference conference)
    {
        this.conference = conference;
    }

}
