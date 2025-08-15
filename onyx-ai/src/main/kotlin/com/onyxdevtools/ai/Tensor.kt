@file:Suppress("DuplicatedCode")

package com.onyxdevtools.ai

import com.onyxdevtools.ai.compute.BasicCPUComputeBackend
import com.onyxdevtools.ai.compute.CPUComputeBackend
import com.onyxdevtools.ai.compute.ComputeBackend
import com.onyxdevtools.ai.compute.MetalComputeBackend
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

fun Tensor(rows: Int, cols: Int): Tensor =
    createTensor(rows, cols)

fun Tensor(
    rows: Int,
    cols: Int,
    init: (r: Int, c: Int) -> Float
): Tensor =
    createTensor(rows, cols, init)

fun Tensor(
    rows: Int,
    cols: Int,
    initRow: (row: Int) -> FloatArray
): Tensor {
    val t = createTensor(rows, cols)
    var r = 0
    while (r < rows) {
        val row = initRow(r)
        require(row.size == cols) { "Row $r length ${row.size} != $cols" }
        var c = 0
        while (c < cols) {
            t[r, c] = row[c]
            c++
        }
        r++
    }
    return t
}

sealed class Tensor {
    abstract val rows: Int
    abstract val cols: Int
    val size: Int get() = rows * cols

    // Absolute element access (row-major).
    abstract operator fun get(r: Int, c: Int): Float
    abstract operator fun set(r: Int, c: Int, v: Float)

    /** Row proxy so you can write tensor[r][c] and tensor[r][c] = v */
    operator fun get(row: Int): Row = Row(this, row)

    // Inside sealed class Tensor
    fun isEmpty(): Boolean = (rows == 0) || (cols == 0)
    fun isNotEmpty(): Boolean = !isEmpty()

    class Row internal constructor(
        private val t: Tensor,
        private val r: Int
    ) {
        val indices: IntRange get() = 0 until size
        val size: Int get() = t.cols
        operator fun get(c: Int): Float = t[r, c]
        operator fun set(c: Int, v: Float) { t[r, c] = v }
        fun toFloatArray(): FloatArray = FloatArray(size).also { t.readRowInto(r, it) }
        fun isEmpty(): Boolean = size == 0
        fun isNotEmpty(): Boolean = !isEmpty()

    }

    /** Range of row indices (compat with Array.indices usage). */
    val indices: IntRange get() = 0 until rows
    /** Explicit row/col index ranges when needed. */
    val rowIndices: IntRange get() = 0 until rows
    val colIndices: IntRange get() = 0 until cols

    /** First row as a copy, or null if empty. Enables: tensor.firstOrNull()?.size */
    fun firstOrNull(): FloatArray? =
        if (rows == 0) null else FloatArray(cols).also { readRowInto(0, it) }

    /** Deep clone (same backing kind as this tensor). */
    fun clone(): Tensor = this.copy()

    /** Release any native / off-heap resources. No-op for heap. */
    open fun dispose() {}

    // ---------------- Iteration helpers ----------------

    inline fun forEach(action: (Float) -> Unit) {
        var r = 0
        while (r < rows) {
            var c = 0
            while (c < cols) {
                action(this[r, c]); c++
            }
            r++
        }
    }

    inline fun forEachIndexed(action: (row: Int, col: Int, value: Float) -> Unit) {
        var r = 0
        while (r < rows) {
            var c = 0
            while (c < cols) {
                action(r, c, this[r, c]); c++
            }
            r++
        }
    }

    /** Reuses a scratch row for each callback (don’t hold onto it). */
    fun forEachRow(action: (rowIndex: Int, row: FloatArray) -> Unit) {
        val scratch = FloatArray(cols)
        var r = 0
        while (r < rows) {
            readRowInto(r, scratch)
            action(r, scratch)
            r++
        }
    }

    open fun readRowInto(row: Int, dest: FloatArray) {
        var c = 0
        while (c < cols) {
            dest[c] = this[row, c]; c++
        }
    }

    open fun asFloatArrayOrNull(): FloatArray? = null
    open fun asFloatBufferOrNull(): FloatBuffer? = null

    // ---------------- Ops (receiver style) ----------------

    /** Matrix multiply: this × other */
    fun multiply(other: Tensor): Tensor = TensorOps.mm(this, other)

    /** Element-wise add: this + other (same shape) */
    fun add(other: Tensor): Tensor = TensorOps.add(this, other)

    /** Element-wise subtract: this - other (same shape) */
    fun subtract(other: Tensor): Tensor = TensorOps.sub(this, other)

    /** Element-wise multiply (Hadamard) */
    fun elementWiseMultiply(other: Tensor): Tensor = TensorOps.mulElem(this, other)

    /** Transpose: this^T */
    fun transpose(): Tensor = TensorOps.transpose(this)

    /** Scalar multiply: scalar × this */
    fun scale(scalar: Float): Tensor = TensorOps.scale(this, scalar)

    /** Adds a vector to each row (vector length == cols). */
    fun addVectorToRows(vector: FloatArray): Tensor = TensorOps.addVectorToRows(this, vector)

    /** Applies transform element-wise. */
    fun map(transform: (Float) -> Float): Tensor = TensorOps.map(this, transform)

    /** Sum of each column. */
    fun sumColumns(): FloatArray = TensorOps.sumColumns(this)

    /** Row-wise softmax. */
    fun softmax(): Tensor = TensorOps.softmax(this)

    /** Mean squared error against another tensor (same shape). */
    fun meanStandardError(other: Tensor): Float = TensorOps.mse(this, other)

    /** Deep copy. */
    fun copy(): Tensor = TensorOps.copy(this)

    companion object {
        @JvmStatic
        fun allocateDirectBuffer(size: Int): FloatBuffer =
            ByteBuffer.allocateDirect(size * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
    }
}

/** Heap-backed tensor (contiguous FloatArray). */
class HeapTensor(
    private val data: FloatArray,
    override val rows: Int,
    override val cols: Int
) : Tensor() {
    init {
        require(rows >= 0 && cols >= 0) { "rows/cols must be non-negative" }
        require(data.size >= rows * cols) { "data too small: ${data.size} < ${rows * cols}" }
    }
    private fun idx(r: Int, c: Int) = r * cols + c
    override operator fun get(r: Int, c: Int): Float = data[idx(r, c)]
    override operator fun set(r: Int, c: Int, v: Float) { data[idx(r, c)] = v }
    override fun readRowInto(row: Int, dest: FloatArray) {
        val from = row * cols
        System.arraycopy(data, from, dest, 0, cols)
    }
    override fun asFloatArrayOrNull(): FloatArray = data
}

/** Metal-backed tensor (FloatBuffer + optional GPU buffer handle for cleanup). */
class MetalTensor(
    private val buffer: FloatBuffer,
    override val rows: Int,
    override val cols: Int,
    internal val metal: MetalComputeBackend? = null,
    private var gpuBufferHandle: Long = 0L,
    private val ownsGpuBuffer: Boolean = false
) : Tensor() {
    init {
        require(rows >= 0 && cols >= 0) { "rows/cols must be non-negative" }
        require(buffer.capacity() >= rows * cols) {
            "buffer too small: ${buffer.capacity()} < ${rows * cols}"
        }
    }
    private fun idx(r: Int, c: Int) = r * cols + c
    override operator fun get(r: Int, c: Int): Float = buffer.get(idx(r, c))
    override operator fun set(r: Int, c: Int, v: Float) { buffer.put(idx(r, c), v) }
    override fun readRowInto(row: Int, dest: FloatArray) {
        val off = row * cols
        var c = 0
        while (c < cols) { dest[c] = buffer.get(off + c); c++ }
    }
    override fun asFloatBufferOrNull(): FloatBuffer = buffer
    override fun dispose() {
        if (ownsGpuBuffer && gpuBufferHandle != 0L && metal != null) {
            try { MetalComputeBackend.releaseGPUBuffer(metal.metalContext, gpuBufferHandle) } catch (_: Throwable) {}
            gpuBufferHandle = 0L
        }
    }
}

/** Generic GPU-backed tensor (FloatBuffer). */
class GPUTensor(
    private val buffer: FloatBuffer,
    override val rows: Int,
    override val cols: Int
) : Tensor() {
    init {
        require(rows >= 0 && cols >= 0) { "rows/cols must be non-negative" }
        require(buffer.capacity() >= rows * cols) {
            "buffer too small: ${buffer.capacity()} < ${rows * cols}"
        }
    }
    private inline fun idx(r: Int, c: Int) = r * cols + c
    override operator fun get(r: Int, c: Int): Float = buffer.get(idx(r, c))
    override operator fun set(r: Int, c: Int, v: Float) { buffer.put(idx(r, c), v) }
    override fun readRowInto(row: Int, dest: FloatArray) {
        val off = row * cols
        var c = 0
        while (c < cols) { dest[c] = buffer.get(off + c); c++ }
    }
    override fun asFloatBufferOrNull(): FloatBuffer = buffer
}

/* ========================== Backend selection (internal) ========================== */

private object Backends {
    val cpu: ComputeBackend by lazy {
        if (isVectorApiAvailable()) CPUComputeBackend() else BasicCPUComputeBackend()
    }
    val metal: MetalComputeBackend? by lazy {
        try {
            if (MetalComputeBackend.isMetalAvailable()) MetalComputeBackend() else null
        } catch (_: Throwable) {
            null
        }
    }
}

private fun isVectorApiAvailable(): Boolean =
    try {
        val cls = Class.forName("jdk.incubator.vector.FloatVector")
        cls.getDeclaredField("SPECIES_PREFERRED").get(null)
        true
    } catch (_: Throwable) { false }

/* ========================== Single factory ========================== */

/**
 * Create a tensor with the best available backing.
 * - Metal available → MetalTensor (direct FloatBuffer)
 * - else → HeapTensor (FloatArray)
 * If [init] provided, it initializes element (r,c).
 */
fun createTensor(rows: Int, cols: Int, init: ((r: Int, c: Int) -> Float)? = null): Tensor {
    require(rows >= 0 && cols >= 0) { "rows/cols must be non-negative" }

    val metal = Backends.metal
    if (metal != null) {
        val buf = Tensor.allocateDirectBuffer(rows * cols)
        if (init != null) {
            var r = 0; var off = 0
            while (r < rows) {
                var c = 0
                while (c < cols) { buf.put(off + c, init(r, c)); c++ }
                off += cols; r++
            }
        } // else already zeroed
        return MetalTensor(buf, rows, cols, metal)
    }

    val data = FloatArray(rows * cols)
    if (init != null) {
        var r = 0; var i = 0
        while (r < rows) {
            var c = 0
            while (c < cols) { data[i++] = init(r, c); c++ }
            r++
        }
    }
    return HeapTensor(data, rows, cols)
}

/* (Optional) keep old helpers for compatibility, but steer callers to createTensor */
@Deprecated("Use createTensor(rows, cols, init) instead", ReplaceWith("createTensor(rows, cols, init)"))
fun heapTensor(rows: Int, cols: Int, init: ((Int, Int) -> Float)? = null): HeapTensor =
    HeapTensor(FloatArray(rows * cols).also { fa ->
        if (init != null) {
            var r = 0; var i = 0
            while (r < rows) { var c = 0; while (c < cols) { fa[i++] = init(r, c); c++ }; r++ }
        }
    }, rows, cols)

@Deprecated("Use createTensor(rows, cols, init) instead", ReplaceWith("createTensor(rows, cols, init)"))
fun metalTensor(rows: Int, cols: Int): MetalTensor =
    MetalTensor(Tensor.allocateDirectBuffer(rows * cols), rows, cols, Backends.metal)

@Deprecated("Use createTensor(rows, cols, init) instead", ReplaceWith("createTensor(rows, cols, init)"))
fun gpuTensor(rows: Int, cols: Int): GPUTensor =
    GPUTensor(Tensor.allocateDirectBuffer(rows * cols), rows, cols)

/* ========================== Internal op router ========================== */

private object TensorOps {
    // Use centrally-chosen backends
    private val cpu get() = Backends.cpu
    private val metalBackend get() = Backends.metal

    fun mm(a: Tensor, b: Tensor): Tensor {
        require(a.cols == b.rows) { "mm shape mismatch: ${a.rows}x${a.cols} × ${b.rows}x${b.cols}" }
        val preferMetal = (a is MetalTensor) || (b is MetalTensor)
        val backend = if (preferMetal && metalBackend != null) metalBackend!! else cpu
        val out = backend.matrixMultiply(a, b)
        return out.toTensorLike(a)
    }

    fun add(a: Tensor, b: Tensor): Tensor {
        require(a.rows == b.rows && a.cols == b.cols) { "add shape mismatch" }
        val backend = if ((a is MetalTensor || b is MetalTensor) && metalBackend != null) metalBackend!! else cpu
        return backend.add(a, b).toTensorLike(a)
    }

    fun sub(a: Tensor, b: Tensor): Tensor {
        require(a.rows == b.rows && a.cols == b.cols) { "subtract shape mismatch" }
        val backend = if ((a is MetalTensor || b is MetalTensor) && metalBackend != null) metalBackend!! else cpu
        return backend.subtract(a, b).toTensorLike(a)
    }

    fun mulElem(a: Tensor, b: Tensor): Tensor {
        require(a.rows == b.rows && a.cols == b.cols) { "element-wise multiply shape mismatch" }
        val backend = if ((a is MetalTensor || b is MetalTensor) && metalBackend != null) metalBackend!! else cpu
        return backend.elementWiseMultiply(a, b).toTensorLike(a)
    }

    fun transpose(x: Tensor): Tensor {
        val backend = if (x is MetalTensor && metalBackend != null) metalBackend!! else cpu
        return backend.transpose(x).toTensorLike(x)
    }

    fun scale(x: Tensor, s: Float): Tensor {
        val backend = if (x is MetalTensor && metalBackend != null) metalBackend!! else cpu
        return backend.scalarMultiply(x, s).toTensorLike(x)
    }

    fun addVectorToRows(x: Tensor, v: FloatArray): Tensor {
        require(v.size == x.cols) { "vector length ${v.size} must equal ${x.cols}" }
        val backend = if (x is MetalTensor && metalBackend != null) metalBackend!! else cpu
        return backend.addVectorToRows(x, v).toTensorLike(x)
    }

    fun map(x: Tensor, f: (Float) -> Float): Tensor {
        // CPU path for arbitrary lambda.
        return cpu.applyElementWise(x, f).toTensorLike(x)
    }

    fun sumColumns(x: Tensor): FloatArray {
        val backend = if (x is MetalTensor && metalBackend != null) metalBackend!! else cpu
        return backend.sumColumns(x)
    }

    fun softmax(x: Tensor): Tensor {
        val backend = if (x is MetalTensor && metalBackend != null) metalBackend!! else cpu
        return backend.softmax(x).toTensorLike(x)
    }

    fun mse(a: Tensor, b: Tensor): Float {
        require(a.rows == b.rows && a.cols == b.cols) { "mse shape mismatch" }
        val backend = if ((a is MetalTensor || b is MetalTensor) && metalBackend != null) metalBackend!! else cpu
        return backend.meanStandardError(a, b)
    }

    fun copy(x: Tensor): Tensor = x.toTensorLike(x)

    private fun Tensor.toTensorLike(like: Tensor): Tensor {
        val m = this.rows
        val n = this.cols
        return when (like) {
            is HeapTensor -> {
                val flat = FloatArray(m * n)
                var dst = 0; var r = 0
                while (r < m) {
                    val row = this[r]
                    System.arraycopy(row, 0, flat, dst, n)
                    dst += n; r++
                }
                HeapTensor(flat, m, n)
            }
            is MetalTensor -> {
                val buf = Tensor.allocateDirectBuffer(m * n)
                var r = 0; var off = 0
                while (r < m) {
                    val row = this[r]
                    var c = 0
                    while (c < n) { buf.put(off + c, row[c]); c++ }
                    off += n; r++
                }
                MetalTensor(buf, m, n, Backends.metal)
            }
            is GPUTensor -> {
                val buf = Tensor.allocateDirectBuffer(m * n)
                var r = 0; var off = 0
                while (r < m) {
                    val row = this[r]
                    var c = 0
                    while (c < n) { buf.put(off + c, row[c]); c++ }
                    off += n; r++
                }
                GPUTensor(buf, m, n)
            }
        }
    }
}

/* ========================== Compatibility helpers ========================== */

/**
 * Keep old feel: apply a row-wise transform and get a Matrix back.
 * The lambda receives a copy of each row to freely mutate.
 */
inline fun Tensor.mapIndexed(
    crossinline transform: (rowIndex: Int, row: FloatArray) -> FloatArray
): Tensor {
    val out = allocateLike(this, rows, cols)
    // reuse scratch rows but pass a copy to user lambda
    var r = 0
    this.forEachRow { rowIndex, rowScratch ->
        val newRow = transform(rowIndex, rowScratch.copyOf())
        require(newRow.size == cols) { "Row $rowIndex length ${newRow.size} != $cols" }
        var c = 0
        while (c < cols) { out[rowIndex, c] = newRow[c]; c++ }
        r++
    }
    return out
}

/** Like above, but returns a Tensor with the same "kind" as `like` (heap/metal/gpu). */
inline fun Tensor.mapIndexedToTensor(
    like: Tensor = this,
    crossinline transform: (rowIndex: Int, row: FloatArray) -> FloatArray
): Tensor {
    val asMatrix = this.mapIndexed(transform)
    return when (like) {
        is HeapTensor -> asMatrix.toTensorHeap()
        is MetalTensor -> asMatrix.toTensorMetal()
        is GPUTensor -> asMatrix.toTensorGPU()
        else -> asMatrix.toTensorHeap()
    }
}

fun Tensor.toTensorHeap(): HeapTensor {
    val m = this.rows; val n = this.cols
    val flat = FloatArray(m * n)
    var dst = 0
    var r = 0
    while (r < m) { val row = this[r]; System.arraycopy(row, 0, flat, dst, n); dst += n; r++ }
    return HeapTensor(flat, m, n)
}

fun Tensor.toTensorMetal(): MetalTensor {
    val m = this.rows; val n = this.cols
    val buf = Tensor.allocateDirectBuffer(m * n)
    var r = 0; var off = 0
    while (r < m) {
        val row = this[r]
        var c = 0
        while (c < n) { buf.put(off + c, row[c]); c++ }
        off += n; r++
    }
    return MetalTensor(buf, m, n, Backends.metal)
}

// Put in the same package as Tensor (e.g., com.onyxdevtools.ai)

/** Sum over rows using a row -> Double selector. Usage: tensor.sumOf { it[j].toDouble() } */
fun Tensor.sumOf(selector: (row: FloatArray) -> Double): Double {
    var sum = 0.0
    // rowScratch is reused; do not retain it outside the lambda
    this.forEachRow { _, rowScratch ->
        sum += selector(rowScratch)
    }
    return sum
}

/** Indexed variant if you need row index too. */
fun Tensor.sumOfIndexed(selector: (rowIndex: Int, row: FloatArray) -> Double): Double {
    var sum = 0.0
    this.forEachRow { r, rowScratch ->
        sum += selector(r, rowScratch)
    }
    return sum
}

/** Average over rows using a selector; returns 0.0 when there are no rows. */
fun Tensor.averageOf(selector: (row: FloatArray) -> Double): Double {
    if (rows == 0) return 0.0
    return this.sumOf(selector) / rows
}

/** Flatten tensor to a single FloatArray for compatibility with test code */
fun Tensor.flatten(): FloatArray {
    val result = FloatArray(rows * cols)
    var index = 0
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            result[index++] = this[r, c]
        }
    }
    return result
}


fun Tensor.toTensorGPU(): GPUTensor {
    val m = this.rows; val n = this.cols
    val buf = Tensor.allocateDirectBuffer(m * n)
    var r = 0; var off = 0
    while (r < m) {
        val row = this[r]
        var c = 0
        while (c < n) { buf.put(off + c, row[c]); c++ }
        off += n; r++
    }
    return GPUTensor(buf, m, n)
}

fun Tensor.subset(rows: List<Int>): Tensor {
    val out = createTensor(rows.size, this.cols)
    var rOut = 0
    while (rOut < rows.size) {
        val rIn = rows[rOut]
        require(rIn in 0 until this.rows) { "Row index $rIn out of bounds 0..${this.rows - 1}" }
        var c = 0
        while (c < this.cols) {
            out[rOut, c] = this[rIn, c]
            c++
        }
        rOut++
    }
    return out
}

fun allocateLike(like: Tensor, rows: Int, cols: Int): Tensor = when (like) {
    is HeapTensor  -> HeapTensor(FloatArray(rows * cols), rows, cols)
    is MetalTensor -> MetalTensor(Tensor.allocateDirectBuffer(rows * cols), rows, cols, null)
    is GPUTensor   -> GPUTensor(Tensor.allocateDirectBuffer(rows * cols), rows, cols)
}

fun Tensor.rowCopy(row: Int): FloatArray = FloatArray(cols).also { readRowInto(row, it) }

// Row helpers (drop anywhere in your ai package)
fun Tensor.Row.average(): Float {
    if (size == 0) return 0f
    var s = 0f; var c = 0
    while (c < size) { s += this[c]; c++ }
    return s / size
}
inline fun Tensor.Row.sumOf(selector: (Float) -> Double): Double {
    var s = 0.0; var c = 0
    while (c < size) { s += selector(this[c]); c++ }
    return s
}

/** Array<FloatArray> (rows x cols) -> Tensor via createTensor initializer. */
fun Array<FloatArray>.toTensor(): Tensor {
    val rows = size
    val cols = if (rows > 0) this[0].size else 0
    require(all { it.size == cols }) { "All rows must have the same length" }
    val src = this
    return createTensor(rows, cols) { r, c -> src[r][c] }
}

/** Convenience overloads if you need them. */
fun List<FloatArray>.toTensor(): Tensor = this.toTypedArray().toTensor()
fun FloatArray.toRowTensor(): Tensor = createTensor(1, size) { _, c -> this[c] }
fun FloatArray.toColTensor(): Tensor = createTensor(size, 1) { r, _ -> this[r] }

/** If you ever need ints -> float Tensor. */
fun Array<IntArray>.toTensor(): Tensor {
    val rows = size
    val cols = if (rows > 0) this[0].size else 0
    require(all { it.size == cols }) { "All rows must have the same length" }
    val src = this
    return createTensor(rows, cols) { r, c -> src[r][c].toFloat() }
}

/* --------------------- Local helpers (Tensor <-> Array) --------------------- */

// Flatten Array<Tensor> (sequence targets) -> Tensor (stacked rows)
fun flattenSequencesToTensor(seqs: Array<Tensor>): Tensor {
    var totalRows = 0
    var cols = 0
    for (t in seqs) {
        totalRows += t.rows
        if (cols == 0 && t.rows > 0) cols = t.cols
    }
    val out = createTensor(totalRows, cols)
    var r = 0
    for (t in seqs) {
        var i = 0
        while (i < t.rows) {
            var c = 0
            while (c < cols) {
                out[r, c] = t[i, c]
                c++
            }
            r++
            i++
        }
    }
    return out
}

// (Optional fallback) Flatten List<Array<FloatArray>> -> Tensor
// Keep this only if other call sites still pass List<Array<FloatArray>>.
private fun flattenSequencesToTensor(seqs: List<Array<FloatArray>>): Tensor {
    var totalRows = 0
    var cols = 0
    for (arr in seqs) {
        totalRows += arr.size
        if (cols == 0 && arr.isNotEmpty()) cols = arr[0].size
    }
    val out = createTensor(totalRows, cols)
    var r = 0
    for (arr in seqs) {
        for (row in arr) {
            var c = 0
            while (c < cols) {
                out[r, c] = row[c]
                c++
            }
            r++
        }
    }
    return out
}
