package com.onyxdevtools.lifecycle.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by tosborn1 on 4/4/16.
 */
@Entity
public class BeverageEffects extends ManagedEntity implements IManagedEntity
{
    public BeverageEffects()
    {

    }

    protected Beverage beverage;

    public BeverageEffects(Beverage beverage)
    {
        this.beverage = beverage;
    }

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected long behaviorId;

    @Attribute
    protected String description;


    /////////////////////////////////////////////////////////////////////
    //
    // Event lifecycle listeners
    //
    /////////////////////////////////////////////////////////////////////

    @PostInsert
    private void beforeInsert()
    {
        this.description = this.beverage.getPreConsumption();
    }

    @PostUpdate
    private void postUpdate()
    {
        this.description = this.beverage.getDuringConsumption();
    }

    @PostRemove
    private void postRemove()
    {
        this.description = this.beverage.getAfterConsumption();
    }

    /////////////////////////////////////////////////////////////////////
    //
    // Getters and setters
    //
    /////////////////////////////////////////////////////////////////////


    public long getBehaviorId() {
        return behaviorId;
    }

    public void setBehaviorId(long behaviorId) {
        this.behaviorId = behaviorId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Beverage getBeverage() {
        return beverage;
    }

    public void setBeverage(Beverage beverage) {
        this.beverage = beverage;
    }
}
