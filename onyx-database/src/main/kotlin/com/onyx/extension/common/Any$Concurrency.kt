package com.onyx.extension.common

import java.util.concurrent.*

typealias Block = Any

val defaultPool = Executors.newWorkStealingPool()!!


/**
 * Run a job using the CommonPool
 *
 * @param block Block expression to execute
 */
fun runJob(interval:Long, unit:TimeUnit, block: () -> Unit): Job {
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
 * Run a block in background co-routine
 *
 * @param block Block expression to execute
 */
fun <T> async(block: () -> T): Future<T> = defaultPool.submit<T> { block.invoke() }

fun delay(amount:Long, unit: TimeUnit) = Thread.sleep(unit.toMillis(amount))

class Job(private val executor:ExecutorService) {

    var active = true
    var future:Future<*>? = null

    fun cancel() {
        active = false
        future?.cancel(true)
        executor.shutdownNow()
    }

    fun join() {
        future?.get()
    }
}