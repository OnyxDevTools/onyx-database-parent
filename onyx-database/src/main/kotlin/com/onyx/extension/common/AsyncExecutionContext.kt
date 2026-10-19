package com.onyx.extension.common

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Propagates explicitly registered thread-local context through [async] tasks.
 * Register application-lifetime locals once, before submitting work that needs them.
 * Context values are captured by reference when a task is submitted, not when it starts.
 */
object AsyncExecutionContext {
    private val threadLocals = CopyOnWriteArrayList<ThreadLocal<Any?>>()

    /** Registers a local once and returns the same instance for convenient declarations. */
    fun <T> register(threadLocal: ThreadLocal<T>): ThreadLocal<T> {
        @Suppress("UNCHECKED_CAST")
        threadLocals.addIfAbsent(threadLocal as ThreadLocal<Any?>)
        return threadLocal
    }

    @PublishedApi
    internal fun capture(): Snapshot = Snapshot(threadLocals.map { it to it.get() })

    @PublishedApi
    internal class Snapshot(private val values: List<Pair<ThreadLocal<Any?>, Any?>>) {
        @PublishedApi
        internal fun <T> run(block: () -> T): T {
            val previous = values.map { (local, _) -> local.get() }
            try {
                values.forEach { (local, value) -> local.restore(value) }
                return block()
            } finally {
                for (index in values.indices.reversed()) {
                    values[index].first.restore(previous[index])
                }
            }
        }

        private fun ThreadLocal<Any?>.restore(value: Any?) {
            if (value == null) remove() else set(value)
        }
    }
}
