package com.onyx.extension.common

import java.nio.charset.Charset

private const val LZ77_HEADER_SIZE = 10
private const val LZ77_VERSION = 1
private const val LZ77_RAW = 0
private const val LZ77_COMPRESSED = 1
private const val LZ77_HASH_LOG = 16
private const val LZ77_MIN_MATCH = 4
private const val LZ77_MAX_DISTANCE = 0xffff

/**
 * Compresses this byte array with a fast LZ77-family codec.
 *
 * The returned value is self-contained and can be restored with [decompressLz77]. The encoder uses
 * a single-pass hash table and a 64 KiB sliding window. Data that would grow during compression is
 * stored verbatim, so the result is never more than ten bytes larger than this array.
 *
 * This codec has its own framing and is not compatible with DEFLATE, gzip, or the 7z container.
 */
fun ByteArray.compressLz77(): ByteArray {
    require(size <= Int.MAX_VALUE - LZ77_HEADER_SIZE) { "Input is too large to frame" }
    if (size < LZ77_MIN_MATCH) return rawLz77Frame(this)

    // This is also the exact raw-frame size, allowing the fallback path to reuse the allocation.
    val output = ByteArray(LZ77_HEADER_SIZE + size)
    writeLz77Header(output, LZ77_COMPRESSED, size)

    // Keep small calls cheap while retaining the full table for larger payloads.
    val hashLog = minOf(
        LZ77_HASH_LOG,
        maxOf(4, Int.SIZE_BITS - Integer.numberOfLeadingZeros(size - 1))
    )
    val hashShift = Int.SIZE_BITS - hashLog
    // Positions are stored plus one so a freshly allocated, zero-filled table needs no initialization.
    val hashTable = IntArray(1 shl hashLog)
    var anchor = 0
    var position = 0
    var outputPosition = LZ77_HEADER_SIZE
    var searchAttempts = 0
    val lastMatchStart = size - LZ77_MIN_MATCH

    while (position <= lastMatchStart) {
        val hash = lz77Hash(this, position, hashShift)
        val candidateEntry = hashTable[hash]
        hashTable[hash] = position + 1
        var candidate = candidateEntry - 1

        if (candidateEntry == 0 ||
            position - candidate > LZ77_MAX_DISTANCE ||
            !lz77IntEquals(this, candidate, position)
        ) {
            // Accelerate through incompressible regions while retaining dense searches near matches.
            position += 1 + (searchAttempts ushr 6)
            searchAttempts++
            continue
        }
        searchAttempts = 0

        // Pull the match backwards when possible. This reduces the literal count at almost no cost.
        while (position > anchor && candidate > 0 && this[position - 1] == this[candidate - 1]) {
            position--
            candidate--
        }

        val literalLength = position - anchor
        val matchStart = position
        val matchCandidate = candidate
        position += LZ77_MIN_MATCH
        candidate += LZ77_MIN_MATCH
        while (position < size && this[position] == this[candidate]) {
            position++
            candidate++
        }

        val matchLength = position - matchStart
        val encodedSequenceSize = 1 +
            encodedLz77LengthSize(literalLength) + literalLength + 2 +
            encodedLz77LengthSize(matchLength - LZ77_MIN_MATCH)
        if (encodedSequenceSize > output.size - outputPosition) {
            return rawLz77Frame(this, output)
        }

        val tokenPosition = outputPosition++
        val literalNibble = minOf(literalLength, 15)
        val matchNibble = minOf(matchLength - LZ77_MIN_MATCH, 15)
        output[tokenPosition] = ((literalNibble shl 4) or matchNibble).toByte()

        if (literalLength >= 15) {
            outputPosition = writeLz77Length(output, outputPosition, literalLength - 15)
        }
        this.copyInto(output, outputPosition, anchor, matchStart)
        outputPosition += literalLength

        val distance = matchStart - matchCandidate
        output[outputPosition++] = distance.toByte()
        output[outputPosition++] = (distance ushr 8).toByte()

        if (matchLength - LZ77_MIN_MATCH >= 15) {
            outputPosition = writeLz77Length(
                output,
                outputPosition,
                matchLength - LZ77_MIN_MATCH - 15
            )
        }

        anchor = position
        if (position - 2 >= 0 && position - 2 <= lastMatchStart) {
            hashTable[lz77Hash(this, position - 2, hashShift)] = position - 1
        }

        // Once the encoded payload cannot beat a raw frame, stop doing work on incompressible input.
        if (outputPosition - LZ77_HEADER_SIZE >= size) return rawLz77Frame(this, output)
    }

    val literalLength = size - anchor
    val finalSequenceSize = 1 + encodedLz77LengthSize(literalLength) + literalLength
    if (outputPosition - LZ77_HEADER_SIZE + finalSequenceSize >= size) {
        return rawLz77Frame(this, output)
    }

    val tokenPosition = outputPosition++
    output[tokenPosition] = (minOf(literalLength, 15) shl 4).toByte()
    if (literalLength >= 15) {
        outputPosition = writeLz77Length(output, outputPosition, literalLength - 15)
    }
    copyInto(output, outputPosition, anchor, size)
    outputPosition += literalLength

    return output.copyOf(outputPosition)
}

/**
 * Encodes this string as bytes with [charset], then compresses it with [compressLz77].
 */
@JvmOverloads
fun String.compressLz77(charset: Charset = Charsets.UTF_8): ByteArray =
    toByteArray(charset).compressLz77()

/**
 * Restores a value produced by [compressLz77].
 *
 * @throws IllegalArgumentException if this array is not a valid LZ77 frame.
 */
fun ByteArray.decompressLz77(): ByteArray {
    require(size >= LZ77_HEADER_SIZE) { "Truncated LZ77 header" }
    require(
        this[0] == 'L'.code.toByte() &&
            this[1] == 'Z'.code.toByte() &&
            this[2] == '7'.code.toByte() &&
            this[3] == '7'.code.toByte()
    ) { "Invalid LZ77 header" }
    require(this[4].toInt() and 0xff == LZ77_VERSION) { "Unsupported LZ77 version" }

    val originalSize = readLz77Int(this, 6)
    require(originalSize >= 0) { "Invalid LZ77 output size" }

    return when (val encoding = this[5].toInt() and 0xff) {
        LZ77_RAW -> {
            require(originalSize == size - LZ77_HEADER_SIZE) { "Invalid raw LZ77 frame size" }
            copyOfRange(LZ77_HEADER_SIZE, size)
        }

        LZ77_COMPRESSED -> decompressLz77Payload(this, originalSize)
        else -> throw IllegalArgumentException("Unsupported LZ77 encoding: $encoding")
    }
}

/**
 * Restores a string compressed with [String.compressLz77], decoding its bytes with [charset].
 */
@JvmOverloads
fun ByteArray.decompressLz77ToString(charset: Charset = Charsets.UTF_8): String =
    decompressLz77().toString(charset)

private fun decompressLz77Payload(input: ByteArray, originalSize: Int): ByteArray {
    val output = ByteArray(originalSize)
    var inputPosition = LZ77_HEADER_SIZE
    var outputPosition = 0

    while (inputPosition < input.size) {
        val token = input[inputPosition++].toInt() and 0xff
        var literalLength = token ushr 4
        if (literalLength == 15) {
            val decoded = readLz77Length(input, inputPosition, literalLength)
            literalLength = (decoded ushr 32).toInt()
            inputPosition = decoded.toInt()
        }

        require(literalLength <= input.size - inputPosition) { "Truncated LZ77 literals" }
        require(literalLength <= output.size - outputPosition) { "LZ77 literals exceed output size" }
        input.copyInto(
            output,
            destinationOffset = outputPosition,
            startIndex = inputPosition,
            endIndex = inputPosition + literalLength
        )
        inputPosition += literalLength
        outputPosition += literalLength

        if (inputPosition == input.size) {
            require(outputPosition == output.size) { "LZ77 output size does not match frame" }
            return output
        }

        require(input.size - inputPosition >= 2) { "Truncated LZ77 match distance" }
        val distance = (input[inputPosition].toInt() and 0xff) or
            ((input[inputPosition + 1].toInt() and 0xff) shl 8)
        inputPosition += 2
        require(distance in 1..outputPosition) { "Invalid LZ77 match distance" }

        var matchLength = (token and 0x0f) + LZ77_MIN_MATCH
        if ((token and 0x0f) == 15) {
            val decoded = readLz77Length(input, inputPosition, matchLength)
            matchLength = (decoded ushr 32).toInt()
            inputPosition = decoded.toInt()
        }
        require(matchLength <= output.size - outputPosition) { "LZ77 match exceeds output size" }

        copyLz77Match(output, outputPosition - distance, outputPosition, matchLength)
        outputPosition += matchLength
        require(inputPosition < input.size) { "Truncated LZ77 payload" }
    }

    require(outputPosition == output.size) { "Truncated LZ77 payload" }
    return output
}

private fun copyLz77Match(output: ByteArray, source: Int, destination: Int, length: Int) {
    val distance = destination - source
    if (distance >= length) {
        output.copyInto(output, destination, source, source + length)
        return
    }

    // Grow an overlapping match exponentially; this is much faster than copying it byte by byte.
    var copied = minOf(distance, length)
    output.copyInto(output, destination, source, source + copied)
    while (copied < length) {
        val chunk = minOf(copied, length - copied)
        output.copyInto(output, destination + copied, destination, destination + chunk)
        copied += chunk
    }
}

private fun readLz77Length(input: ByteArray, start: Int, base: Int): Long {
    var length = base
    var position = start
    var next: Int
    do {
        require(position < input.size) { "Truncated LZ77 length" }
        next = input[position++].toInt() and 0xff
        require(length <= Int.MAX_VALUE - next) { "LZ77 length overflow" }
        length += next
    } while (next == 255)
    return (length.toLong() shl 32) or (position.toLong() and 0xffffffffL)
}

private fun rawLz77Frame(
    input: ByteArray,
    output: ByteArray = ByteArray(LZ77_HEADER_SIZE + input.size)
): ByteArray {
    writeLz77Header(output, LZ77_RAW, input.size)
    input.copyInto(output, LZ77_HEADER_SIZE)
    return output
}

private fun encodedLz77LengthSize(length: Int): Int =
    if (length < 15) 0 else ((length - 15) / 255) + 1

private fun writeLz77Header(output: ByteArray, encoding: Int, originalSize: Int) {
    output[0] = 'L'.code.toByte()
    output[1] = 'Z'.code.toByte()
    output[2] = '7'.code.toByte()
    output[3] = '7'.code.toByte()
    output[4] = LZ77_VERSION.toByte()
    output[5] = encoding.toByte()
    output[6] = originalSize.toByte()
    output[7] = (originalSize ushr 8).toByte()
    output[8] = (originalSize ushr 16).toByte()
    output[9] = (originalSize ushr 24).toByte()
}

private fun readLz77Int(input: ByteArray, offset: Int): Int =
    (input[offset].toInt() and 0xff) or
        ((input[offset + 1].toInt() and 0xff) shl 8) or
        ((input[offset + 2].toInt() and 0xff) shl 16) or
        ((input[offset + 3].toInt() and 0xff) shl 24)

private fun writeLz77Length(output: ByteArray, start: Int, value: Int): Int {
    var remaining = value
    var position = start
    while (remaining >= 255) {
        output[position++] = 255.toByte()
        remaining -= 255
    }
    output[position++] = remaining.toByte()
    return position
}

private fun lz77Hash(input: ByteArray, offset: Int, shift: Int): Int {
    val value = readLz77Int(input, offset)
    return (value * -1640531535) ushr shift
}

private fun lz77IntEquals(input: ByteArray, first: Int, second: Int): Boolean =
    input[first] == input[second] &&
        input[first + 1] == input[second + 1] &&
        input[first + 2] == input[second + 2] &&
        input[first + 3] == input[second + 3]
