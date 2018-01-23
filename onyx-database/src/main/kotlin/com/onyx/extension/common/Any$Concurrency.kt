package com.onyx.extension.common

import java.util.concurrent.*

typealias Block = Any

val defaultPool: ExecutorService = Executors.newWorkStealingPool()!!

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
 * Run a block in background on the default pool.  The default pool should be implemented as a ForkJoinPool
 *
 * @param block Block expression to execute
 * @since 2.0.0
 */
inline fun <T> async(crossinline block: () -> T): Future<T> = defaultPool.submit<T> { block.invoke() }

/**
 * Run a block in background on the default pool.  The default pool should be implemented as a ForkJoinPool
 *
 * @param block Block expression to execute
 * @since 2.0.0
 */
inline fun <T> async(executor: ExecutorService, crossinline block: () -> T): Future<T> = executor.submit<T> { block() }


inline fun <A, B>List<A>.parallelMap(parallel:Boolean = true, crossinline block: (A) -> B): List<B> {
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
}

inline fun <A> List<A>.parallelForEach(crossinline block: (A) -> Unit) {
    val futures:MutableList<Future<Unit>> = ArrayList()
    this.forEach {
        futures.add(async {
            block.invoke(it)
        })

        if(futures.size > 100) {
            futures.forEach {
                it.get()
            }
            futures.clear()
        }
    }
    futures.forEach { it.get() }
}

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