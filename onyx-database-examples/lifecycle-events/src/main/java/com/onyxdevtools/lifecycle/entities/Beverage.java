package com.onyxdevtools.lifecycle.entities;

/**
 * Created by tosborn1 on 4/4/16.
 *
 * These are the possible beverages that lead to different behaviors.
 */
@SuppressWarnings("unused")
public enum Beverage
{
    BEER("Im feeling dangerous", "Lets makeout on a plane and annoy the people around me!", "Hold my hair back"),
    WATER("Soooo thirsty", "Satisfied", "I gotta pee"),
    COFFEE("Very Sleepy", "Bouncing off the walls", "Zzzzzzzzzz");

    protected String preConsumption;

    protected String duringConsumption;

    protected String afterConsumption;

    /**
     * Constructor for Beverage includes the descriptions of the effects of the beverage
     * @param preConsumption How you are feeling prior to consuming a beverage
     * @param duringConsumption How you feel during the consumption of a beverage
     * @param afterConsumption How you feel after consuming a beverage
     */
    Beverage(String preConsumption, String duringConsumption, String afterConsumption)
    {
        this.preConsumption = preConsumption;
        this.duringConsumption = duringConsumption;
        this.afterConsumption = afterConsumption;
    }

    public String getPreConsumption() {
        return preConsumption;
    }

    public void setPreConsumption(String preConsumption) {
        this.preConsumption = preConsumption;
    }

    public String getDuringConsumption() {
        return duringConsumption;
    }

    public void setDuringConsumption(String duringConsumption) {
        this.duringConsumption = duringConsumption;
    }

    public String getAfterConsumption() {
        return afterConsumption;
    }

    public void setAfterConsumption(String afterConsumption) {
        this.afterConsumption = afterConsumption;
    }
}
