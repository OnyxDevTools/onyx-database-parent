package com.onyx.interactors.transaction.impl

internal const val WAL_FORCE_INTERVAL_MILLIS = 20L

internal fun interface PeriodicWalForceTarget {
    fun force()
}

/**
 * Forces every active memory-mapped WAL from one process-wide daemon thread.
 * The worker exists only while at least one database has an open WAL.
 */
internal object PeriodicWalFlusher {

    private const val THREAD_NAME = "onyx-wal-force"

    private val lifecycleLock = Any()
    private val targets = linkedSetOf<PeriodicWalForceTarget>()
    private var worker: Thread? = null

    fun register(target: PeriodicWalForceTarget) {
        synchronized(lifecycleLock) {
            if (!targets.add(target)) return
            if (worker == null) startWorker()
        }
    }

    fun unregister(target: PeriodicWalForceTarget) {
        val workerToWake = synchronized(lifecycleLock) {
            targets.remove(target)
            worker?.takeIf { targets.isEmpty() }
        }
        workerToWake?.interrupt()
    }

    internal fun registeredTargetCount(): Int = synchronized(lifecycleLock) {
        targets.size
    }

    internal fun currentWorker(): Thread? = synchronized(lifecycleLock) {
        worker
    }

    private fun startWorker() {
        check(Thread.holdsLock(lifecycleLock))
        check(worker == null)

        worker = Thread(
            { runWorker(Thread.currentThread()) },
            THREAD_NAME
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun runWorker(currentThread: Thread) {
        try {
            while (true) {
                val currentTargets = synchronized(lifecycleLock) {
                    if (worker !== currentThread || targets.isEmpty()) return
                    targets.toList()
                }

                currentTargets.forEach { target ->
                    try {
                        target.force()
                    } catch (_: Throwable) {
                        unregister(target)
                    }
                }

                try {
                    Thread.sleep(WAL_FORCE_INTERVAL_MILLIS)
                } catch (_: InterruptedException) {
                    // Registration state is checked again at the top of the loop.
                }
            }
        } finally {
            synchronized(lifecycleLock) {
                if (worker === currentThread) {
                    worker = null
                    if (targets.isNotEmpty()) startWorker()
                }
            }
        }
    }
}
