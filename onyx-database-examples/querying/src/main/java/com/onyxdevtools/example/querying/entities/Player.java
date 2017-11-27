package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chris Osborn
 */
//J-
@Entity
@SuppressWarnings("unused")
public class Player extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    private int playerId;

    @Attribute
    private String firstName;

    @Attribute
    private String lastName;

    @Attribute
    private String position;

    @Attribute
    private boolean active = true;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "players",
            inverseClass = Team.class
    )
    private Team team;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverse = "player",
            inverseClass = Stats.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY
    )
    private List<Stats> stats = new ArrayList<>();

    public Player()
    {
    }

    public int getPlayerId()
    {
        return playerId;
    }

    public void setPlayerId(int playerId)
    {
        this.playerId = playerId;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    public String getPosition()
    {
        return position;
    }

    public void setPosition(String position)
    {
        this.position = position;
    }

    public Team getTeam()
    {
        return team;
    }

    public void setTeam(Team team)
    {
        this.team = team;
    }

    public List<Stats> getStats()
    {
        return stats;
    }

    public void setStats(List<Stats> stats)
    {
        this.stats = stats;
    }

    public boolean getActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

}
