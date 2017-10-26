package com.onyxdevtools.provider.manager;

import com.onyxdevtools.provider.DatabaseProvider;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Tim Osborn on 8/26/16.
 *
 * This is the JPA implementation of the persistence manager.  This is setup to have 8 concurrent entity managers
 * Although that may not be the best implementation for application's business rules, this is used to outline
 * performance rather than business use.  8 Concurrent entity managers seems to provide the optimal performance.
 */
public class JPAPersistenceManager implements ProviderPersistenceManager {

    private static final int CONCURRENT_ENTITY_MANAGERS = 8;
    // Database type
    private final DatabaseProvider databaseProvider;

    // Available entity managers to use
    private final BlockingQueue<EntityManager> entityManagers;

    // We must use a single entity manager for reading data while performing insertions with the remainder of the pool
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final EntityManager entityManagerForRead;

    /**
     * Constructor with factory and type
     *
     * @param entityManagerFactory The factory for instantiating the entity managers
     * @param databaseProvider What type of database
     */
    public JPAPersistenceManager(EntityManagerFactory entityManagerFactory, DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
        this.entityManagerForRead = entityManagerFactory.createEntityManager();
        this.entityManagers = new LinkedBlockingQueue<>(CONCURRENT_ENTITY_MANAGERS);
        for(int i = 0; i < CONCURRENT_ENTITY_MANAGERS; i++)
            this.entityManagers.add(entityManagerFactory.createEntityManager());
    }

    public void update(Object object) {
        EntityManager entityManager = null;

        try {
            entityManager = entityManagers.poll();
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                entityManager.merge(object);
                transaction.commit();
            } catch (Exception e) {
                transaction.rollback();
                e.printStackTrace();
            }
        } finally {
            if(entityManager != null)
                entityManagers.add(entityManager);
        }
    }

    /**
     * Insert an entity that must not already exist within the database.  Within JPA this uses the persist method
     * as opposed to the merge.
     *
     * @param object Object to insert
     */
    public void insert(Object object) {
        EntityManager entityManager = null;
        try {
            entityManager = entityManagers.poll();
            entityManager.getTransaction().begin();
            try {

                entityManager.persist(object);
                entityManager.getTransaction().commit();
            } catch (Exception e) {
                e.printStackTrace();
                entityManager.getTransaction().rollback();
            }
        } finally {
            if(entityManager != null)
                entityManagers.add(entityManager);
        }
    }

    /**
     * Delete an entity within the database
     *
     * @param clazz Entity type
     * @param identifier Primary key of the entity
     */
    @SuppressWarnings("UNCHECKED_CAST")
    public void delete(Class clazz, Object identifier) {

        EntityManager entityManager = null;
        try {
            entityManager = entityManagers.poll();
            entityManager.getTransaction().begin();
            try {

                @SuppressWarnings("unchecked") Object object = entityManager.find(clazz, identifier);
                entityManager.remove(object);
                entityManager.getTransaction().commit();
            } catch (Exception e) {
                e.printStackTrace();
                entityManager.getTransaction().rollback();
            }
        } finally {
            if(entityManager != null)
                entityManagers.add(entityManager);
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
        EntityManager entityManager = null;
        try {
            entityManager = entityManagers.poll();
            //noinspection unchecked
            return entityManager.find(clazz, identifier);
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if(entityManager != null)
                entityManagers.add(entityManager);
        }

        return null;
    }

    /**
     * Execute query with criteria of key = key.  This will auto generate a sql statement.
     *
     * @param clazz Entity to query
     * @param key Attribute to predicate upon
     * @param value Attribute key to predicate upon
     * @return List of entities that match the criteria
     */
    public List list(Class clazz, String key, Object value)
    {
        EntityManager entityManager = null;
        try {
            entityManager = entityManagers.poll();

            Query query = entityManager.createQuery("SELECT c FROM " + clazz.getCanonicalName() + " c where c." + key + " = :param");
            query.setParameter("param", value);
            return query.getResultList();

        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if(entityManager != null)
                entityManagers.add(entityManager);
        }

        return null;
    }

    /**
     * The database type the persistence manager was instantiated with
     * @return The database type
     */
    public DatabaseProvider getDatabaseProvider() {
        return databaseProvider;
    }
}
