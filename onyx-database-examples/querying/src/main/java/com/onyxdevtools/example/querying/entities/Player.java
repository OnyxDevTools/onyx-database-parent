package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.RelationshipType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cosborn
 */
//J-
@Entity
public class Player extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected int playerId;

    @Attribute
    protected String firstName;

    @Attribute
    protected String lastName;

    @Attribute
    protected String position;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "players",
            inverseClass = Team.class
    )
    protected Team team;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverse = "player",
            inverseClass = Stats.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY
    )
    protected List<Stats> stats = new ArrayList<>();

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

}
