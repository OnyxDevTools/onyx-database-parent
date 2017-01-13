package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import javax.persistence.*;
import javax.persistence.Index;

/**
 *
 * @author cosborn
 */
//J-
@com.onyx.persistence.annotations.Entity(fileName = "stats")
@javax.persistence.Entity
@Table(indexes = { @Index(name = "rushingYards", columnList = "rushingYards", unique = false) })
public class Stats extends ManagedEntity implements IManagedEntity
{
    public Stats()
    {

    }

    public Stats(long id)
    {
        this.statId = id;
    }
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE, loadFactor = 2)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    protected long statId;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "stats",
            inverseClass = Player.class,
            loadFactor = 1
    )
    @ManyToOne(targetEntity = Player.class)
    protected Player player;

    @Relationship(
            type = RelationshipType.ONE_TO_ONE,
            inverseClass = Season.class,
            loadFactor = 1
    )
    @OneToOne
    protected Season season;

    @Attribute
    @Column
    @com.onyx.persistence.annotations.Index(loadFactor = 2)
    protected int rushingYards;

    @Attribute
    @Column
    protected int receivingYards;

    @Attribute
    @Column
    protected int passingYards;

    @Attribute
    @Column
    protected int rushingTouchdowns;

    @Attribute
    @Column
    protected int receivingTouchdowns;

    @Attribute
    @Column
    protected int passingTouchdowns;

    @Attribute
    @Column
    protected int receptions;

    @Attribute
    @Column
    protected int rushingAttempts;

    @Attribute
    @Column
    protected int passAttempts;

    @Attribute
    @Column
    protected int passCompletions;

    @Attribute
    @Column
    protected int fantasyPoints;

    public long getStatId()
    {
        return statId;
    }

    @SuppressWarnings("unused")
    public void setStatId(long statId)
    {
        this.statId = statId;
    }

    @SuppressWarnings("unused")
    public Player getPlayer()
    {
        return player;
    }

    public void setPlayer(Player player)
    {
        this.player = player;
    }

    @SuppressWarnings("unused")
    public Season getSeason()
    {
        return season;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public int getReceivingYards()
    {
        return receivingYards;
    }

    @SuppressWarnings("unused")
    public void setReceivingYards(int receivingYards)
    {
        this.receivingYards = receivingYards;
    }

    @SuppressWarnings("unused")
    public int getPassingYards()
    {
        return passingYards;
    }

    public void setPassingYards(int passingYards)
    {
        this.passingYards = passingYards;
    }

    @SuppressWarnings("unused")
    public int getRushingTouchdowns()
    {
        return rushingTouchdowns;
    }

    public void setRushingTouchdowns(int rushingTouchdowns)
    {
        this.rushingTouchdowns = rushingTouchdowns;
    }

    @SuppressWarnings("unused")
    public int getReceivingTouchdowns()
    {
        return receivingTouchdowns;
    }

    @SuppressWarnings("unused")
    public void setReceivingTouchdowns(int receivingTouchdowns)
    {
        this.receivingTouchdowns = receivingTouchdowns;
    }

    @SuppressWarnings("unused")
    public int getPassingTouchdowns()
    {
        return passingTouchdowns;
    }

    @SuppressWarnings("unused")
    public void setPassingTouchdowns(int passingTouchdowns)
    {
        this.passingTouchdowns = passingTouchdowns;
    }

    @SuppressWarnings("unused")
    public int getReceptions()
    {
        return receptions;
    }

    public void setReceptions(int receptions)
    {
        this.receptions = receptions;
    }

    @SuppressWarnings("unused")
    public int getRushingAttempts()
    {
        return rushingAttempts;
    }

    public void setRushingAttempts(int rushingAttempts)
    {
        this.rushingAttempts = rushingAttempts;
    }

    @SuppressWarnings("unused")
    public int getPassAttempts()
    {
        return passAttempts;
    }

    public void setPassAttempts(int passAttempts)
    {
        this.passAttempts = passAttempts;
    }

    @SuppressWarnings("unused")
    public int getPassCompletions()
    {
        return passCompletions;
    }

    @SuppressWarnings("unused")
    public void setPassCompletions(int passCompletions)
    {
        this.passCompletions = passCompletions;
    }

    @SuppressWarnings("unused")
    public int getFantasyPoints()
    {
        return fantasyPoints;
    }

    public void setFantasyPoints(int fantasyPoints)
    {
        this.fantasyPoints = fantasyPoints;
    }

}
