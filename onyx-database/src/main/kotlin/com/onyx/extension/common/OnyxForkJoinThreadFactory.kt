package com.onyx.extension.common

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread

object OnyxForkJoinThreadFactory : ForkJoinPool.ForkJoinWorkerThreadFactory {
    override fun newThread(pool: ForkJoinPool): ForkJoinWorkerThread = OnyxThread(pool)
}