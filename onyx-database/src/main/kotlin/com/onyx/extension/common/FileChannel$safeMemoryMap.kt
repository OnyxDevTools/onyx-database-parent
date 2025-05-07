package com.onyx.extension.common

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Safely memory-maps a region of this [FileChannel] for read/write access, with built-in retry logic
 * for [OutOfMemoryError].
 *
 * This function attempts to map the specified region of the file. If an [OutOfMemoryError] occurs
 * (either directly or as the cause of an [IOException]), it will:
 * 1. Print a message indicating low direct memory.
 * 2. Invoke the provided [onException] lambda, which can be used to attempt to free up resources.
 * 3. Suggest garbage collection via [System.gc].
 * 4. Wait for a period that increases with each attempt (250ms * attempt number).
 * 5. Retry the mapping operation.
 * This process repeats indefinitely until the mapping is successful.
 *
 * @receiver The [FileChannel] to map.
 * @param offset The position within the file at which the mapped region is to start; must be non-negative.
 * @param size The size of the region to be mapped; must be non-negative and no greater than [Integer.MAX_VALUE].
 * @param onException A lambda function that is invoked when an [OutOfMemoryError] is caught.
 * This allows the caller to perform custom actions, such as trying to release memory
 * (e.g., by clearing caches or forcing eviction of other mapped buffers).
 * @return A [ByteBuffer] representing the memory-mapped region.
 * @throws IOException if an I/O error occurs that is not caused by an [OutOfMemoryError] during the mapping attempt.
 */
fun FileChannel.safeMemoryMap(
    offset: Long,
    size: Int,
    onException: () -> Unit
): ByteBuffer {
    var attempts = 1L
    while(true) {
        try {
            return this.map(FileChannel.MapMode.READ_WRITE, offset, size.toLong())
        } catch (e: OutOfMemoryError) {
            println("Direct Memory is critically low.  Attempting to release memory")
            onException.invoke()
            System.gc()
            Thread.sleep(250 * attempts)
            attempts += 1
        } catch (e: IOException) {
            if (e.cause is OutOfMemoryError) {
                println("Direct Memory is critically low.  Attempting to release memory")
                onException.invoke()
                System.gc()
                Thread.sleep(250 * attempts)
                attempts += 1
            }
        }
    }
}
