package com.onyxdevtools.provider.manager;

import com.onyx.exception.InitializationException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.provider.DatabaseProvider;

import java.util.List;

/**
 * Created by tosborn1 on 8/26/16.
 *
 * Onyx Persistence manager designed for CRUD operations
 */
public class OnyxPersistenceManager implements ProviderPersistenceManager {


    private PersistenceManager persistenceManager;

    /**
     * Constructor with persistence manager
     * @param persistenceManager Onyx Persistence manager that interfaces with the Onyx API
     */
    public OnyxPersistenceManager(PersistenceManager persistenceManager)
    {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Update an entity that must already exist.  Within onyx there is no differentiation
     * between inserting and updating so either way it will call the saveEntity method
     *
     * @param object Object to update
     */
    public void update(Object object) {
        try {
            persistenceManager.saveEntity((IManagedEntity) object);
        } catch (InitializationException ex){}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert an entity that must not already exist within the database.  Within onyx there is no differentiation
     * between inserting and updating so either way it will call the saveEntity method
     *
     * @param object Object to insert
     */
    public void insert(Object object) {
        try {
            persistenceManager.saveEntity((IManagedEntity) object);
        } catch (InitializationException ex){}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete an entity within the database
     *
     * @param clazz Entity type
     * @param identifier Primary key of the entity
     */
    public void delete(Class clazz, Object identifier) {
        try {
            persistenceManager.deleteEntity(persistenceManager.findById(clazz, identifier));
        } catch (InitializationException ex){}
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Find an entity with a given type and primary key
     *
     * @param clazz Entity type
     * @param identifier Entity's primary key
     * @return Object within database if found
     */
    public Object find(Class clazz, Object identifier) {
        try {
            return persistenceManager.findById(clazz, identifier);
        } catch (InitializationException ex){}
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Execute query with criteria of key = key
     *
     * @param clazz Entity to query
     * @param key Attribute to predicate upon
     * @param value Attribute key to predicate upon
     * @return List of entities that match the criteria
     */
    public List list(Class clazz, String key, Object value)
    {
        QueryCriteria criteria = null;
        if(value instanceof String)
            criteria = new QueryCriteria(key, QueryCriteriaOperator.EQUAL, (String)value);
        else if(value.getClass() == int.class || value instanceof Integer)
            criteria = new QueryCriteria(key, QueryCriteriaOperator.EQUAL, (Integer) value);

        Query query = new Query(clazz, criteria);
        try {
            return persistenceManager.executeLazyQuery(query);
        } catch (InitializationException ex){}
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * The database type the persistence manager was instantiated with
     * @return The database type
     */
    public DatabaseProvider getDatabaseProvider() {
        return DatabaseProvider.ONYX;
    }
}
