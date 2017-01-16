package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 *
 * @author cosborn
 */
//J-
@Entity
public class Stats extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected long statId;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "stats",
            inverseClass = Player.class
    )
    protected Player player;

    @Relationship(
            type = RelationshipType.ONE_TO_ONE,
            inverseClass = Season.class
    )
    protected Season season;

    @Attribute
    protected int rushingYards;

    @Attribute
    protected int receivingYards;

    @Attribute
    protected int passingYards;

    @Attribute
    protected int rushingTouchdowns;

    @Attribute
    protected int receivingTouchdowns;

    @Attribute
    protected int passingTouchdowns;

    @Attribute
    protected int receptions;

    @Attribute
    protected int rushingAttempts;

    @Attribute
    protected int passAttempts;

    @Attribute
    protected int passCompletions;

    @Attribute
    protected int fantasyPoints;

    public long getStatId()
    {
        return statId;
    }

    public void setStatId(long statId)
    {
        this.statId = statId;
    }

    public Player getPlayer()
    {
        return player;
    }

    public void setPlayer(Player player)
    {
        this.player = player;
    }

    public Season getSeason()
    {
        return season;
    }

    public void setSeason(Season season)
    {
        this.season = season;
    }

    public int getRushingYards()
    {
        return rushingYards;
    }

    public void setRushingYards(int rushingYards)
    {
        this.rushingYards = rushingYards;
    }

    public int getReceivingYards()
    {
        return receivingYards;
    }

    public void setReceivingYards(int receivingYards)
    {
        this.receivingYards = receivingYards;
    }

    public int getPassingYards()
    {
        return passingYards;
    }

    public void setPassingYards(int passingYards)
    {
        this.passingYards = passingYards;
    }

    public int getRushingTouchdowns()
    {
        return rushingTouchdowns;
    }

    public void setRushingTouchdowns(int rushingTouchdowns)
    {
        this.rushingTouchdowns = rushingTouchdowns;
    }

    public int getReceivingTouchdowns()
    {
        return receivingTouchdowns;
    }

    public void setReceivingTouchdowns(int receivingTouchdowns)
    {
        this.receivingTouchdowns = receivingTouchdowns;
    }

    public int getPassingTouchdowns()
    {
        return passingTouchdowns;
    }

    public void setPassingTouchdowns(int passingTouchdowns)
    {
        this.passingTouchdowns = passingTouchdowns;
    }

    public int getReceptions()
    {
        return receptions;
    }

    public void setReceptions(int receptions)
    {
        this.receptions = receptions;
    }

    public int getRushingAttempts()
    {
        return rushingAttempts;
    }

    public void setRushingAttempts(int rushingAttempts)
    {
        this.rushingAttempts = rushingAttempts;
    }

    public int getPassAttempts()
    {
        return passAttempts;
    }

    public void setPassAttempts(int passAttempts)
    {
        this.passAttempts = passAttempts;
    }

    public int getPassCompletions()
    {
        return passCompletions;
    }

    public void setPassCompletions(int passCompletions)
    {
        this.passCompletions = passCompletions;
    }

    public int getFantasyPoints()
    {
        return fantasyPoints;
    }

    public void setFantasyPoints(int fantasyPoints)
    {
        this.fantasyPoints = fantasyPoints;
    }

}
