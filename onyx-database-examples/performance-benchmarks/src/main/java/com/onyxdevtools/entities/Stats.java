package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import javax.persistence.*;
import javax.persistence.Index;

/**
 *
 * @author Chris Osborn
 */
//J-
@com.onyx.persistence.annotations.Entity(fileName = "stats")
@javax.persistence.Entity
@Table(indexes = { @Index(name = "rushingYards", columnList = "rushingYards") })
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
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long statId;

    @Relationship(
            type = RelationshipType.MANY_TO_ONE,
            inverse = "stats",
            inverseClass = Player.class
    )
    @ManyToOne(targetEntity = Player.class)
    private Player player;

    @Relationship(
            type = RelationshipType.ONE_TO_ONE,
            inverseClass = Season.class
    )
    @OneToOne
    private Season season;

    @Attribute
    @Column
    @com.onyx.persistence.annotations.Index
    private int rushingYards;

    @Attribute
    @Column
    private int receivingYards;

    @Attribute
    @Column
    private int passingYards;

    @Attribute
    @Column
    private int rushingTouchdowns;

    @Attribute
    @Column
    private int receivingTouchdowns;

    @Attribute
    @Column
    private int passingTouchdowns;

    @Attribute
    @Column
    private int receptions;

    @Attribute
    @Column
    private int rushingAttempts;

    @Attribute
    @Column
    private int passAttempts;

    @Attribute
    @Column
    private int passCompletions;

    @Attribute
    @Column
    private int fantasyPoints;

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
