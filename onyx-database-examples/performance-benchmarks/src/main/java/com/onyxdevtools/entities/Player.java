package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Chris Osborn
 */
//J-
@Entity
@javax.persistence.Entity
public class Player extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 2)
    @Id
    private int playerId;

    @Attribute
    @Column
    private String firstName;

    @Attribute
    @Column
    private String lastName;

    @Attribute
    @Column
    private String position;

    @Attribute
    @Column
    private boolean active = true;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "players",
            inverseClass = Team.class,
            loadFactor = 1
    )
    @ManyToOne(targetEntity = Team.class)
    private Team team;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverse = "player",
            inverseClass = Stats.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY,
            loadFactor = 1
    )
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, targetEntity = Stats.class, mappedBy = "player")
    private List<Stats> stats = new ArrayList<>();

    @SuppressWarnings("unused")
    public Player()
    {
    }

    public Player(int playerId)
    {
        this.playerId = playerId;
    }

    public int getPlayerId()
    {
        return playerId;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    @SuppressWarnings("unused")
    public String getPosition()
    {
        return position;
    }

    public void setPosition(String position)
    {
        this.position = position;
    }

    @SuppressWarnings("unused")
    public Team getTeam()
    {
        return team;
    }

    public void setTeam(Team team)
    {
        this.team = team;
    }

    @SuppressWarnings("unused")
    public List<Stats> getStats()
    {
        return stats;
    }

    public void setStats(List<Stats> stats)
    {
        this.stats = stats;
    }

    @SuppressWarnings("unused")
    public boolean getActive()
    {
        return active;
    }

    public void setActive(@SuppressWarnings("SameParameterValue") boolean active)
    {
        this.active = active;
    }

}
