package com.onyx.lucene.interactors

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serializes lifecycle operations for a physical Lucene index.
 *
 * The locks are striped rather than stored per index so closed partition indexes do not leave
 * an unbounded collection of lock objects behind. A collision only causes unrelated indexes to
 * briefly serialize commits or lifecycle changes.
 */
internal object LuceneLifecycle {
    private const val LOCK_STRIPES = 64
    private val locks = Array(LOCK_STRIPES) { ReentrantLock() }

    fun <T> withIndexLock(indexKey: String, action: () -> T): T {
        val lockIndex = Math.floorMod(indexKey.hashCode(), locks.size)
        return locks[lockIndex].withLock(action)
    }
}
