package com.onyx.extension

import kotlinx.coroutines.experimental.*
import java.util.concurrent.atomic.AtomicBoolean


// Interface for blocking object
interface Blocking {
    var blocked: AtomicBoolean
}

// Blocking hash map for use within runBlockingOn
class BlockingHashMap<K,V>(override var blocked: AtomicBoolean = AtomicBoolean(false)) : HashMap<K, V>(), Blocking

/**
 * Run blocking on an object.
 *
 * If the object is marked as blocked, it will add it to a looper within a coroutine.
 * If it is not unblocked, it will run the block expression.
 *
 * The purpose of this is not to incur the slowness and lameness of synchronized and not
 * have to execute everything within a looper coroutine
 *
 * @param blocking Blocking object that contains a lock
 * @param body Block expression to execute
 *
 */
fun <T> runBlockingOn(blocking: Blocking, body: () -> T): T {
    try {
        return if(blocking.blocked.getAndSet(true)) runBlocking { body.invoke() } else body.invoke()
    } finally {
        blocking.blocked.set(false)
    }
}

/**
 * Run a job using the CommonPool
 *
 * @param block Block expression to execute
 */
fun runJob(block: suspend CoroutineScope.() -> Unit): Job = kotlinx.coroutines.experimental.launch(context = CommonPool, block = block)

/**
 * Run a block in background coroutine
 *
 * @param block Block expression to execute
 */
fun <T> async(block: suspend CoroutineScope.() -> T): Deferred<T> = kotlinx.coroutines.experimental.async(context = CommonPool, block = block)

