package com.onyx.stream;

import com.onyx.persistence.manager.PersistenceManager;

import java.util.Objects;

/**
 * Created by tosborn1 on 5/19/16.
 *
 * This is a lambda for the Onyx Database Stream API.  This will iterate through entities
 */
@FunctionalInterface
public interface QueryStream<T>
{
    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument is a PersistenceManager
     */
    void accept(T t, PersistenceManager u);
}
