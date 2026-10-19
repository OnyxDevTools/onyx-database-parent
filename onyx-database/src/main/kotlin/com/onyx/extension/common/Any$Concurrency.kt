package com.onyx.extension.common

import java.util.concurrent.*

typealias Block = Any

// Partition scans, sequence producers and listener callbacks can block and submit
// other async work. Keep this executor elastic, with idle workers parked and
// expired, and isolate CPU row collection in its own bounded executor. A fixed
// shared pool can starve nested work; virtual threads can pin their carriers in
// listener callbacks holding Java monitors on our JDK 23 runtime.
val defaultPool: ExecutorService = Executors.newCachedThreadPool(
    Thread.ofPlatform().daemon(true).name("onyx-async-", 0).factory()
)

/**
 * Run a job using a single thread executor.  This will run a daemon thread and pause between iterations to save CPU
 * cycles.
 *
 * @param block Block expression to execute
 * @since 2.0.0
 */
inline fun runJob(interval:Long, unit:TimeUnit, crossinline block: () -> Unit): Job {
    val executor = Executors.newSingleThreadExecutor()

    val job = Job(executor = executor)
    job.future = executor.submit {
        while (job.active) {
            block.invoke()
            delay(interval, unit)
        }
    }

    return job
}

/**
 * Run a block in the background on an elastic daemon pool, allowing nested blocking async work.
 *
 * @param block Block expression to execute
 * @since 2.0.0
 */
inline fun <T> async(crossinline block: () -> T): Future<T> = defaultPool.submit<T> { block.invoke() }

/**
 * Run a block in the background on the supplied executor.
 *
 * @param block Block expression to execute
 * @since 2.0.0
 */
inline fun <T> async(executor: ExecutorService, crossinline block: () -> T): Future<T> = executor.submit<T> { block() }


/*
inline fun <A, B>Collection<A>.parallelMap(parallel:Boolean = true, crossinline block: (A) -> B): List<B> {
    return if(parallel) {
        val returnValue: MutableList<B> = ArrayList()
        this.parallelForEach {
            val value = block(it)
            synchronized(returnValue) {
                returnValue.add(value)
            }
        }
        returnValue
    } else {
        map(block)
    }
}*/

/**
 * Process rows in chunks using a bounded CPU executor. The caller also processes
 * rows; nested invocations or a busy executor make progress on the calling thread.
 * Every started callback finishes before this method returns or throws.
 */
inline fun <A> Collection<A>.parallelForEach(crossinline block: (A) -> Unit) =
    processRows(this) { block(it) }


/**
 * Sleep thread a fixed amount of time.
 *
 * @param amount of time to sleep
 * @param unit TimeUnit value
 * @since 2.0.0
 */
fun delay(amount:Long, unit: TimeUnit) = Thread.sleep(unit.toMillis(amount))

/**
 * Job.  This class is a handle of a single thread executor running a daemon thread
 * @since 2.0.0
 */
class Job(private val executor:ExecutorService) {

    var active = true
    var future:Future<*>? = null

    /**
     * Cancel the job and shut down the executor
     * @since 2.0.0
     */
    fun cancel() {
        active = false
        future?.cancel(true)
        executor.shutdownNow()
    }

    /**
     * Join the job thread and block until completed
     * @since 2.0.0
     */
    fun join() {
        future?.get()
    }
}

/**
 * Deferred Value is a substitute for Completable future.  Since we do not want to rely on Java 8 because of Android
 * this was added.  The only difference is that this uses a count down latch but functionality should be the same
 *
 * @since 2.0.0
 */
class DeferredValue<T> {

    private val countDown = CountDownLatch(1)
    var value:T? = null

    fun complete(value:T?) {
        this.value = value
        countDown.countDown()
    }

    @Throws(TimeoutException::class)
    fun get(timeout:Long, unit: TimeUnit):T? {
        val success = countDown.await(timeout, unit)
        if(!success)
            throw TimeoutException()
        return value
    }
}
