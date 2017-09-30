package com.onyx.concurrent

/**
 * Created by tosborn1 on 3/2/17.
 *
 *
 * This method is the contract that is used to count long values.
 * The implementation of it is usually an AtomicLong.  The purpose
 * of it is so the implementation can be replaced in order to
 * make a distributed counter
 */
interface AtomicCounter {

    /**
     * Set counter to long value
     *
     * @param value count
     * @since 1.3.0
     */
    fun set(value: Long)

    /**
     * Get Value of counter
     *
     * @return count
     * @since 1.3.0
     */
    fun get(): Long

    /**
     * Thread safe, get the current value and then add
     *
     * @param more How many more bytes
     * @return The current value before adding
     * @since 1.3.0
     */
    fun getAndAdd(more: Int): Long
}
