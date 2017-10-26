package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
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
public class Team extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(loadFactor = 1)
    @Id
    private String teamName;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverse = "team",
            inverseClass = Player.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY,
            loadFactor = 1
    )
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "team") // Hack, I cannot fetch multiple bags so, I had to change the fetch policy
    private List<Player> players;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "teams",
            inverseClass = Division.class,
            loadFactor = 1
    )
    @ManyToOne(targetEntity = Division.class)
    private Division division;

    public Team()
    {
    }

    public String getTeamName()
    {
        return teamName;
    }

    public void setTeamName(String teamName)
    {
        this.teamName = teamName;
    }

    @SuppressWarnings("unused")
    public List<Player> getPlayers()
    {
        return players;
    }

    public void setPlayers(List<Player> players)
    {
        this.players = players;
    }

    @SuppressWarnings("unused")
    public void setDivision(Division division)
    {
        this.division = division;
    }

    @SuppressWarnings("unused")
    public Division getDivision()
    {
        return this.division;
    }
}
