package com.onyx.extension.common

import kotlinx.coroutines.experimental.*

typealias Block = Any

/**
 * Run a job using the CommonPool
 *
 * @param block Block expression to execute
 */
fun runJob(name:String, block: suspend CoroutineScope.() -> Unit): Job = kotlinx.coroutines.experimental.launch(newSingleThreadContext(name), block = block)

/**
 * Run a block in background co-routine
 *
 * @param block Block expression to execute
 */
fun <T> async(block: suspend CoroutineScope.() -> T): Deferred<T> = kotlinx.coroutines.experimental.async(context = CommonPool, block = block)

