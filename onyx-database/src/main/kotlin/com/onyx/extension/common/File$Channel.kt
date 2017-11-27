package com.onyx.extension.common

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * Open the data file
 *
 * @return File channel
 */
fun String.openFileChannel(): FileChannel? {
    val channel: FileChannel
    val file = File(this)
    try {
        // Create the data file if it does not exist
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        // Open the random access file
        val randomAccessFile = RandomAccessFile(this, "rw")
        channel = randomAccessFile.channel
        channel.position(channel.size())
    } catch (e: IOException) {
        return null
    }

    return channel
}

/**
 * Close a file channel.  First syncs the channel and then closes it
 *
 * @throws IOException Basic IO Exception.  Nothing special
 */
fun FileChannel.closeAndFlush() {
    this.force(true)
    this.close()
}