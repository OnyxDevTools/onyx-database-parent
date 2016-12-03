package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.RelationshipType;

import javax.persistence.*;
import java.util.List;

/**
 * @author cosborn
 */
//J-
@Entity
@javax.persistence.Entity
public class Team extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    @Id
    protected String teamName;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverse = "team",
            inverseClass = Player.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY
    )
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "team") // Hack, I cannot fetch multiple bags so, I had to change the fetch policy
    protected List<Player> players;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "teams",
            inverseClass = Division.class
    )
    @ManyToOne(targetEntity = Division.class)
    protected Division division;

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

}
