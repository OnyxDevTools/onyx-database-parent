package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveAction
import kotlin.math.*
import kotlin.math.exp as dexp // Kotlin's exp is Double

/**
 * Basic CPU compute backend that works on all platforms including Android.
 * This implementation uses only standard Kotlin/Java features without any 
 * platform-specific optimizations like Java Vector API.
 * 
 * This serves as the base implementation that other CPU backends can extend
 * with platform-specific optimizations.
 */
open class BasicCPUComputeBackend : ComputeBackend {

    private val PARALLEL_THRESHOLD = 50_000

    override val backendType: ComputeBackendType = ComputeBackendType.CPU

    override fun matrixMultiply(a: Tensor, b: Tensor): Tensor {
        require(a[0].size == b.size) { 
            "Matrix dimensions don't match for multiplication: ${a.size}x${a[0].size} * ${b.size}x${b[0].size}" 
        }

        val numRows = a.size
        val sharedDim = a[0].size
        val numCols = b[0].size
        return Tensor(numRows, numCols) { row, col ->
            var sum = 0.0f
            val rowA = a[row]
            for (sharedIndex in 0 until sharedDim) {
                sum += rowA[sharedIndex] * b[sharedIndex][col]
            }
            sum
        }
    }

    private inline fun forEachChunk(total: Int, chunks: Int, body: (start: Int, end: Int, chunkIdx: Int) -> Unit) {
        if (total <= 0 || chunks <= 1) { body(0, total, 0); return }
        val base = total / chunks
        val rem  = total % chunks
        var start = 0
        for (i in 0 until chunks) {
            val size = base + if (i < rem) 1 else 0
            val end = start + size
            if (size > 0) body(start, end, i)
            start = end
        }
    }

    private fun maybeParallelByRows(rows: Int, cols: Int, block: (rStart: Int, rEnd: Int) -> Unit) {
        val ops = rows * cols
        if (ops <= PARALLEL_THRESHOLD || rows <= 1) {
            block(0, rows)
            return
        }
        val cores = min(Runtime.getRuntime().availableProcessors(), rows)
        val tasks = ArrayList<RecursiveAction>(cores)
        forEachChunk(rows, cores) { rs, re, _ ->
            tasks += object : RecursiveAction() {
                override fun compute() { block(rs, re) }
            }
        }
        ForkJoinTask.invokeAll(tasks)
    }

    /* -------------------- Elementwise ops -------------------- */

    override fun add(a: Tensor, b: Tensor): Tensor {
        // Supports element-wise addition with optional row or column broadcast
        val rows = a.size
        val cols = a.columnSize
        require(!a.isEmpty() && (a.size == b.size && a.columnSize == b.columnSize
            || b.rows == 1 && b.columnSize == cols
            || b.columnSize == 1 && b.rows == rows)) {
            "Shape mismatch for add: (${a.size}x${a.columnSize}) + (${b.rows}x${b.columnSize})"
        }
        if (rows == 0 || cols == 0) return Tensor(0, 0)
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            for (r in rs until re) {
                val ar = a[r]
                val orow = out[r]
                when {
                    b.size == a.size && b.columnSize == cols -> {
                        val br = b[r]
                        for (c in 0 until cols) orow[c] = ar[c] + br[c]
                    }
                    b.rows == 1 && b.columnSize == cols -> {
                        val br0 = b[0]
                        for (c in 0 until cols) orow[c] = ar[c] + br0[c]
                    }
                    b.columnSize == 1 && b.rows == rows -> {
                        val bv = b[r, 0]
                        for (c in 0 until cols) orow[c] = ar[c] + bv
                    }
                }
            }
        }
        return out
    }

    override fun subtract(a: Tensor, b: Tensor): Tensor {
        // Supports element-wise subtraction with optional row or column broadcast
        val rows = a.size
        val cols = a.columnSize
        require(!a.isEmpty() && (a.size == b.size && a.columnSize == b.columnSize
            || b.rows == 1 && b.columnSize == cols
            || b.columnSize == 1 && b.rows == rows)) {
            "Shape mismatch for subtract: (${a.size}x${a.columnSize}) - (${b.rows}x${b.columnSize})"
        }
        if (rows == 0 || cols == 0) return Tensor(0, 0)
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            for (r in rs until re) {
                val ar = a[r]
                val orow = out[r]
                when {
                    b.size == a.size && b.columnSize == cols -> {
                        val br = b[r]
                        for (c in 0 until cols) orow[c] = ar[c] - br[c]
                    }
                    b.rows == 1 && b.columnSize == cols -> {
                        val br0 = b[0]
                        for (c in 0 until cols) orow[c] = ar[c] - br0[c]
                    }
                    b.columnSize == 1 && b.rows == rows -> {
                        val bv = b[r, 0]
                        for (c in 0 until cols) orow[c] = ar[c] - bv
                    }
                }
            }
        }
        return out
    }

    override fun elementWiseMultiply(a: Tensor, b: Tensor): Tensor {
        // Supports element-wise multiply with optional row or column broadcast
        val rows = a.size
        val cols = a.columnSize
        require(!a.isEmpty() && (a.size == b.size && a.columnSize == b.columnSize
            || b.rows == 1 && b.columnSize == cols
            || b.columnSize == 1 && b.rows == rows)) {
            "Shape mismatch for elementWiseMultiply: (${a.size}x${a.columnSize}) ⊙ (${b.rows}x${b.columnSize})"
        }
        if (rows == 0 || cols == 0) return Tensor(0, 0)
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            for (r in rs until re) {
                val ar = a[r]
                val orow = out[r]
                when {
                    b.size == a.size && b.columnSize == cols -> {
                        val br = b[r]
                        for (c in 0 until cols) orow[c] = ar[c] * br[c]
                    }
                    b.rows == 1 && b.columnSize == cols -> {
                        val br0 = b[0]
                        for (c in 0 until cols) orow[c] = ar[c] * br0[c]
                    }
                    b.columnSize == 1 && b.rows == rows -> {
                        val bv = b[r, 0]
                        for (c in 0 until cols) orow[c] = ar[c] * bv
                    }
                }
            }
        }
        return out
    }

    override fun scalarMultiply(tensor: Tensor, scalar: Float): Tensor {
        val rows = tensor.size
        val cols = tensor.columnSize
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            var r = rs
            while (r < re) {
                val tr = tensor[r]
                val orow = out[r]
                var c = 0
                while (c < cols) {
                    orow[c] = tr[c] * scalar
                    c++
                }
                r++
            }
        }
        return out
    }

    override fun addVectorToRows(tensor: Tensor, vector: FloatArray): Tensor {
        val rows = tensor.size
        val cols = tensor.columnSize
        require(vector.size == cols) { "Vector length ${vector.size} must equal tensor column size $cols" }
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            var r = rs
            while (r < re) {
                val tr = tensor[r]
                val orow = out[r]
                var c = 0
                while (c < cols) {
                    orow[c] = tr[c] + vector[c]
                    c++
                }
                r++
            }
        }
        return out
    }

    override fun applyElementWise(tensor: Tensor, transform: (Float) -> Float): Tensor {
        val rows = tensor.size
        val cols = tensor.columnSize
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            var r = rs
            while (r < re) {
                val tr = tensor[r]
                val orow = out[r]
                var c = 0
                while (c < cols) {
                    orow[c] = transform(tr[c])
                    c++
                }
                r++
            }
        }
        return out
    }

    /* -------------------- Transpose (tiled for cache) -------------------- */

    override fun transpose(tensor: Tensor): Tensor {
        val rows = tensor.size
        val cols = tensor.columnSize
        if (rows == 0 || cols == 0) return Tensor(0, 0)

        val out = Tensor(cols, rows)
        val tile = 32 // good default for L1/L2

        // Parallelize by row tiles if large
        val ops = rows * cols
        if (ops <= PARALLEL_THRESHOLD) {
            var r0 = 0
            while (r0 < rows) {
                val rEnd = min(r0 + tile, rows)
                var c0 = 0
                while (c0 < cols) {
                    val cEnd = min(c0 + tile, cols)
                    var r = r0
                    while (r < rEnd) {
                        val inRow = tensor[r]
                        var c = c0
                        while (c < cEnd) {
                            out[c][r] = inRow[c]
                            c++
                        }
                        r++
                    }
                    c0 += tile
                }
                r0 += tile
            }
        } else {
            val cores = min(Runtime.getRuntime().availableProcessors(), (rows + tile - 1) / tile)
            val tasks = ArrayList<RecursiveAction>(cores)
            forEachChunk(rows, cores) { rs, re, _ ->
                val startTile = (rs / tile) * tile
                val endTile   = re
                tasks += object : RecursiveAction() {
                    override fun compute() {
                        var r0 = startTile
                        while (r0 < endTile) {
                            val rEnd = min(r0 + tile, rows)
                            var c0 = 0
                            while (c0 < cols) {
                                val cEnd = min(c0 + tile, cols)
                                var r = r0
                                while (r < rEnd) {
                                    val inRow = tensor[r]
                                    var c = c0
                                    while (c < cEnd) {
                                        out[c][r] = inRow[c]
                                        c++
                                    }
                                    r++
                                }
                                c0 += tile
                            }
                            r0 += tile
                        }
                    }
                }
            }
            ForkJoinTask.invokeAll(tasks)
        }
        return out
    }

    /* -------------------- Reductions / row-wise -------------------- */

    override fun sumColumns(tensor: Tensor): FloatArray {
        val rows = tensor.size
        val cols = tensor.columnSize
        if (rows == 0 || cols == 0) return FloatArray(cols)

        val ops = rows * cols
        if (ops <= PARALLEL_THRESHOLD) {
            val out = FloatArray(cols)
            var r = 0
            while (r < rows) {
                val tr = tensor[r]
                var c = 0
                while (c < cols) {
                    out[c] += tr[c]
                    c++
                }
                r++
            }
            return out
        }

        val cores = min(Runtime.getRuntime().availableProcessors(), rows)
        val partials = Array(cores) { FloatArray(cols) }
        val tasks = ArrayList<RecursiveAction>(cores)
        forEachChunk(rows, cores) { rs, re, idx ->
            tasks += object : RecursiveAction() {
                override fun compute() {
                    val acc = partials[idx]
                    var r = rs
                    while (r < re) {
                        val tr = tensor[r]
                        var c = 0
                        while (c < cols) {
                            acc[c] += tr[c]
                            c++
                        }
                        r++
                    }
                }
            }
        }
        ForkJoinTask.invokeAll(tasks)

        // reduce partials
        val out = FloatArray(cols)
        var i = 0
        while (i < cores) {
            val acc = partials[i]
            var c = 0
            while (c < cols) {
                out[c] += acc[c]
                c++
            }
            i++
        }
        return out
    }

    /* -------------------- Softmax (numerically stable, row-wise) -------------------- */

    override fun softmax(tensor: Tensor): Tensor {
        val rows = tensor.size
        val cols = tensor.columnSize
        val out = Tensor(rows, cols)

        maybeParallelByRows(rows, cols) { rs, re ->
            var r = rs
            while (r < re) {
                val inRow = tensor[r]
                val outRow = out[r]

                // 1) find max
                var maxVal = Float.NEGATIVE_INFINITY
                var c = 0
                while (c < cols) {
                    val v = inRow[c]
                    if (v > maxVal) maxVal = v
                    c++
                }

                // 2) exponentiate (shifted) into outRow, accumulate sum
                var sumExp = 0.0
                c = 0
                while (c < cols) {
                    val e = dexp((inRow[c] - maxVal).toDouble())
                    outRow[c] = e.toFloat()
                    sumExp += e
                    c++
                }

                // 3) normalize
                val inv = (1.0 / sumExp).toFloat()
                c = 0
                while (c < cols) {
                    outRow[c] *= inv
                    c++
                }
                r++
            }
        }
        return out
    }
}
