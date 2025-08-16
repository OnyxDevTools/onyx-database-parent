package com.onyxdevtools.ai

import com.onyxdevtools.ai.compute.BasicCPUComputeBackend

val basicCompute = BasicCPUComputeBackend()

/**
 * Row-major 2D tensor backed by a single FloatArray.
 * - Index like: tensor[i][k]  (row "view" then column)
 * - Or: tensor[i, k]
 * - Iterates over rows: for (row in tensor) { for (x in row) { ... } }
 * - size == number of rows; columnSize == number of columns
 */
class Tensor internal constructor(
    val rows: Int,
    val cols: Int,
    private var data: FloatArray?,
    private var gpuHandle: Long? = null
) : Iterable<Tensor.Row> {

    init {
        require(rows >= 0 && cols >= 0) { "rows=$rows cols=$cols must be non-negative" }
        require(data.size == rows * cols) { "Backing size ${data.size} != rows*cols=${rows * cols}" }
    }

    /** number of rows */
    val size: Int get() = rows

    /** number of columns */
    val columnSize: Int get() = cols

    /** 2D access: tensor[i, j] */
    operator fun get(row: Int, col: Int): Float {
        checkBounds(row, col)
        return data[offsetOf(row, col)]
    }

    /** 2D write: tensor[i, j] = v */
    operator fun set(row: Int, col: Int, value: Float) {
        checkBounds(row, col)
        data[offsetOf(row, col)] = value
    }

    /** Row view for tensor[i][k] syntax */
    operator fun get(row: Int): Row {
        checkRow(row)
        return Row(this, row)
    }

    /** Replace an entire row from a FloatArray (length must equal columnSize). */
    operator fun set(row: Int, values: FloatArray) {
        checkRow(row)
        require(values.size == cols) { "Expected ${cols} columns, got ${values.size}" }
        System.arraycopy(values, 0, data, rowOffset(row), cols)
    }

    /** Deep copy (copies the flattened storage). */
    fun deepCopy(): Tensor = Tensor(rows, cols, data.copyOf())

    /** Iterate row-by-row (each Row supports iteration over columns). */
    override fun iterator(): Iterator<Row> = object : Iterator<Row> {
        private var r = 0
        override fun hasNext() = r < rows
        override fun next(): Row = Row(this@Tensor, r++)
    }

    override fun toString(): String = "Tensor(${rows}x${cols})"

    // ---- Row view ----------------------------------------------------------

    class Row internal constructor(
        private val base: Tensor,
        private val row: Int
    ) : Iterable<Float> {

        constructor(size: Int, init: () -> Float) : this(
            Tensor(1, size) { _, _ -> init() },
            0
        )

        constructor(size: Int, init: (Int) -> Float) : this(
            Tensor(1, size) { _, c -> init(c) },
            0
        )

        constructor(size: Int, fill: Float) : this(
            Tensor(1, size) { _, _ -> fill },
            0
        )

        val size: Int get() = base.cols
        /**
         * Range of valid column indices for this row: 0 until [size].
         */
        val indices: IntRange get() = 0 until size

        operator fun get(col: Int): Float {
            return base.data[base.rowOffset(row) + col]
        }

        operator fun set(col: Int, value: Float) {
            base.data[base.rowOffset(row) + col] = value
        }

        /** Copy this row into [dst] at [dstOffset]. */
        fun copyInto(dst: FloatArray, dstOffset: Int = 0) {
            System.arraycopy(base.data, base.rowOffset(row), dst, dstOffset, base.cols)
        }

        override fun iterator(): Iterator<Float> = object : Iterator<Float> {
            private var c = 0
            override fun hasNext() = c < base.cols
            override fun next(): Float = base.data[base.rowOffset(row) + c++]
        }
    }

    // ---- Constructors / builders ------------------------------------------

    /** Zero-initialized tensor. */
    constructor(rows: Int, cols: Int) : this(rows, cols, FloatArray(rows * cols))

    /** Builder with initializer (r, c) -> value. */
    constructor(rows: Int, cols: Int, init: (row: Int, col: Int) -> Float) :
            this(rows, cols, FloatArray(rows * cols)) {
        var i = 0
        for (r in 0 until rows) for (c in 0 until cols) data[i++] = init(r, c)
    }

    private fun rowOffset(row: Int) = row * cols
    private fun offsetOf(row: Int, col: Int) = row * cols + col

    private fun checkRow(row: Int) {
        if (row !in 0 until rows) throw IndexOutOfBoundsException("row=$row size=$rows")
    }

    private fun checkBounds(row: Int, col: Int) {
        if (row !in 0 until rows) throw IndexOutOfBoundsException("row=$row size=$rows")
        if (col !in 0 until cols) throw IndexOutOfBoundsException("col=$col size=$cols")
    }

    val indices: IntRange get() = 0 until rows
    val colIndices: IntRange get() = 0 until cols
    fun isEmpty() = rows == 0 || cols == 0

// ---- Reusable ops (drop-in) -----------------------------------------------

    /**
     * Element-wise addition with optional row or column broadcast using CPU backend.
     */
    fun add(other: Tensor): Tensor = basicCompute.add(this, other)
    operator fun plus(other: Tensor): Tensor = add(other)

    // Element-wise minus / times / div (with simple broadcasting like add).
    /**
     * Element-wise subtraction with optional row or column broadcast using CPU backend.
     */
    fun sub(other: Tensor): Tensor = basicCompute.subtract(this, other)
    operator fun minus(other: Tensor): Tensor = sub(other)

    // Hadamard product (element-wise multiply) with simple broadcasting.
    /**
     * Hadamard product (element-wise multiply) with optional row or column broadcast using CPU backend.
     */
    fun hadamard(other: Tensor): Tensor = basicCompute.elementWiseMultiply(this, other)
    operator fun times(other: Tensor): Tensor = hadamard(other)

    /**
     * Scale by a scalar using CPU backend.
     */
    fun scale(alpha: Float): Tensor = basicCompute.scalarMultiply(this, alpha)

    /**
     * Transpose using CPU backend.
     */
    fun transpose(): Tensor = basicCompute.transpose(this)

    // Copy src row into dst row (same col count).
    fun copyRowFrom(src: Tensor, srcRow: Int, dstRow: Int) {
        require(this.cols == src.cols) { "copyRowFrom: column mismatch ${this.cols} vs ${src.cols}" }
        checkRow(dstRow); src.checkRow(srcRow)
        var c = 0
        while (c < cols) {
            this[dstRow, c] = src[srcRow, c]
            c++
        }
    }

    // Fill / zero helpers
    fun fill(value: Float) {
        var i = 0
        while (i < data.size) { data[i] = value; i++ }
    }
    fun zero() = fill(0.0f)
    fun zeroRow(row: Int) {
        checkRow(row)
        val base = rowOffset(row)
        var c = 0
        while (c < cols) { data[base + c] = 0.0f; c++ }
    }


    /**
     * Softmax (row-wise) using CPU backend.
     */
    fun softmaxRowsInto(dst: Tensor) {
        require(dst.rows == rows && dst.cols == cols) { "softmaxRowsInto shape mismatch" }
        val out = basicCompute.softmax(this)
        System.arraycopy(out.data, 0, dst.data, 0, out.data.size)
    }

    /**
     * Softmax (row-wise) using CPU backend.
     */
    fun softmaxRows(): Tensor = basicCompute.softmax(this)

    // Dot between row segments: dot( this[row, off:off+len), other[row2, off:off+len) )
    fun dotRowSegment(row: Int, other: Tensor, otherRow: Int, offset: Int, length: Int): Float {
        checkRow(row); other.checkRow(otherRow)
        require(offset >= 0 && length >= 0 && offset + length <= cols && offset + length <= other.cols) {
            "dotRowSegment bounds"
        }
        var sum = 0.0f
        var d = 0
        while (d < length) {
            sum += this[row, offset + d] * other[otherRow, offset + d]
            d++
        }
        return sum
    }

// ---- Factories ------------------------------------------------------------

    companion object {
        fun zeros(rows: Int, cols: Int): Tensor = Tensor(rows, cols)
        fun ones(rows: Int, cols: Int): Tensor {
            val t = Tensor(rows, cols)
            var i = 0
            while (i < t.data.size) { t.data[i] = 1.0f; i++ }
            return t
        }
        fun from(matrix: Array<FloatArray>): Tensor {
            val r = matrix.size
            val c = if (r == 0) 0 else matrix[0].size
            val flat = FloatArray(r * c)
            var dst = 0
            var i = 0
            while (i < r) {
                require(matrix[i].size == c) { "Jagged matrix at row $i" }
                val row = matrix[i]
                System.arraycopy(row, 0, flat, dst, c)
                dst += c
                i++
            }
            return Tensor(r, c, flat)
        }
    }
}
