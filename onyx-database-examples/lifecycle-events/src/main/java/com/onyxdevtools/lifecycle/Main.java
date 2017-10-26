package com.onyxdevtools.lifecycle;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.impl.CacheManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.lifecycle.entities.Beverage;
import com.onyxdevtools.lifecycle.entities.BeverageEffects;

/**
 * Created by Tim Osborn on 4/4/16.
 *
 * This demo highlights usage of lifecycle events associated to entities.
 */
public class Main extends AbstractDemo {

    public static void main(String[] args) throws OnyxException {

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