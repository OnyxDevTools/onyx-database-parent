package com.onyx.concurrent

interface DispatchReadWriteLock {

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param consumer Function to invoke
     * @return The result from the function
     */
    fun <T> readLock(consumer: () -> T): T

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param consumer Function to invoke
     * @return The result from the function
     */
    fun <T> optimisticReadLock(consumer: () -> T): T

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param consumer Function to invoke
     * @return The result from the function
     */
    fun <T> writeLock(consumer: () -> T): T
}