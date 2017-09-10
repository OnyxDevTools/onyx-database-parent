package com.onyx.extension

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job

typealias Block = Any

/**
 * Run a job using the CommonPool
 *
 * @param block Block expression to execute
 */
fun runJob(block: suspend CoroutineScope.() -> Unit): Job = kotlinx.coroutines.experimental.launch(context = CommonPool, block = block)

/**
 * Run a block in background co-routine
 *
 * @param block Block expression to execute
 */
fun <T> async(block: suspend CoroutineScope.() -> T): Deferred<T> = kotlinx.coroutines.experimental.async(context = CommonPool, block = block)

