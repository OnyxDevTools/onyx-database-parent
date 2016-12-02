package com.onyxdevtools.provider.manager;

import com.onyxdevtools.provider.DatabaseProvider;

import java.util.List;

/**
 * Created by tosborn1 on 8/26/16.
 *
 * Contract for interacting with database
 *
 */
public interface ProviderPersistenceManager {

    /**
     * Update an entity that must already exist.
     *
     * @param object Object to update
     */
    void update(Object object);

    /**
     * Insert an entity that must not already exist within the database
     *
     * @param object Object to insert
     */
    void insert(Object object);

    /**
     * Delete an entity within the database
     *
     * @param clazz Entity type
     * @param identifier Primary key of the entity
     */
    void delete(Class clazz, Object identifier);

    /**
     * Find an entity with a given type and primary key
     *
     * @param clazz Entity type
     * @param identifier Entity's primary key
     * @return Object within database if found
     */
    Object find(Class clazz, Object identifier);

    /**
     * Execute query with criteria of key = value
     *
     * @param clazz Entity to query
     * @param key Attribute to predicate upon
     * @param value Attribute value to predicate upon
     * @return List of entities that match the criteria
     */
    List list(Class clazz, String key, Object value);

    /**
     * The database type the persistence manager was instantiated with
     * @return The database type
     */
    DatabaseProvider getDatabaseProvider();
}
