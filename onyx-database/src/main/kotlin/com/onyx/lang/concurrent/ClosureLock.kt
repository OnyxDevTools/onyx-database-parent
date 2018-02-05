package com.onyx.lang.concurrent

interface ClosureLock {

    /**
     * This method performs a lambda function by locking on whatever object you pass in.
     *
     * @param consumer Function to invoke
     * @return The result from the function
     */
    fun <T> perform(consumer: () -> T): T
}