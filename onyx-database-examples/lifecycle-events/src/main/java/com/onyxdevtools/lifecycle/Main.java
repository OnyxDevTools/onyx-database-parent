package com.onyxdevtools.lifecycle;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.impl.CacheManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.lifecycle.entities.Beverage;
import com.onyxdevtools.lifecycle.entities.BeverageEffects;

import java.io.IOException;

/**
 * Created by tosborn1 on 4/4/16.
 */
public class Main extends AbstractDemo {

    public static void main(String[] args) throws EntityException {

        //Initialize the database and get a handle on the PersistenceManager
        CacheManagerFactory factory = new CacheManagerFactory();
        factory.initialize();
        PersistenceManager manager = factory.getPersistenceManager();

        // Define a beverage effect
        BeverageEffects effectsOfWater = new BeverageEffects(Beverage.WATER);

        // Observe the behavior of the pre insert listener
        manager.saveEntity(effectsOfWater);
        assertTrue("After saving the behavior entity, the effects should be thirsty", effectsOfWater.getDescription().equals(Beverage.WATER.getPreConsumption()));

        // Observe the behavior of the pre update listener
        manager.saveEntity(effectsOfWater);
        assertTrue("After updating the behavior entity, the effects should be satisfied", effectsOfWater.getDescription().equals(Beverage.WATER.getDuringConsumption()));

        // Observe the behavior of the pre delete listener
        manager.deleteEntity(effectsOfWater);
        assertTrue("Before deleting the behavior entity, the effects should be, I gotta pee", effectsOfWater.getDescription().equals(Beverage.WATER.getAfterConsumption()));

        factory.close();
    }
}