package com.onyxdevtools.ai

import kotlin.reflect.KClass

/**
 * A flexible matrix interface that supports both Float and Double precision
 * to optimize memory usage based on the precision requirements.
 */
sealed interface FlexibleMatrix {
    val rows: Int
    val cols: Int
    
    /**
     * Gets a value at the specified position as Double (converts if needed)
     */
    operator fun get(row: Int, col: Int): Double
    
    /**
     * Sets a value at the specified position (converts if needed)
     */
    operator fun set(row: Int, col: Int, value: Double)
    
    /**
     * Gets a row as DoubleArray (converts if needed)
     */
    operator fun get(row: Int): DoubleArray
    
    /**
     * Creates a deep copy of this matrix
     */
    fun deepCopy(): FlexibleMatrix
    
    /**
     * Converts to a standard Array<DoubleArray> for backward compatibility
     */
    fun toDoubleMatrix(): Array<DoubleArray>
    
    /**
     * Converts to Array<FloatArray> if possible, otherwise converts from double
     */
    fun toFloatMatrix(): Array<FloatArray>
    
    /**
     * Returns true if this matrix uses single precision (Float)
     */
    val isSinglePrecision: Boolean
}

/**
 * Double precision matrix implementation
 */
class DoubleMatrix(
    override val rows: Int,
    override val cols: Int,
    internal val data: Array<DoubleArray> = Array(rows) { DoubleArray(cols) }
) : FlexibleMatrix {
    
    constructor(data: Array<DoubleArray>) : this(data.size, if (data.isEmpty()) 0 else data[0].size, data)
    
    override val isSinglePrecision: Boolean = false
    
    // Direct access methods for performance
    override fun get(row: Int, col: Int): Double = data[row][col]
    
    override fun set(row: Int, col: Int, value: Double) {
        data[row][col] = value
    }
    
    // Direct array access without copying
    override fun get(row: Int): DoubleArray = data[row]
    
    override fun deepCopy(): FlexibleMatrix = DoubleMatrix(rows, cols, data.map { it.copyOf() }.toTypedArray())
    
    override fun toDoubleMatrix(): Array<DoubleArray> = data.map { it.copyOf() }.toTypedArray()
    
    override fun toFloatMatrix(): Array<FloatArray> = data.map { row -> 
        FloatArray(row.size) { row[it].toFloat() } 
    }.toTypedArray()
    
    /**
     * Gets the underlying DoubleArray for efficient direct access - NO COPYING
     */
    fun getDoubleArrayDirect(row: Int): DoubleArray = data[row]
    
    /**
     * Gets the underlying data for maximum performance - USE WITH CAUTION
     */
    fun getInternalData(): Array<DoubleArray> = data
}

/**
 * Single precision matrix implementation for memory efficiency
 */
class FloatMatrix(
    override val rows: Int,
    override val cols: Int,
    private val data: Array<FloatArray> = Array(rows) { FloatArray(cols) }
) : FlexibleMatrix {
    
    constructor(data: Array<FloatArray>) : this(data.size, if (data.isEmpty()) 0 else data[0].size, data)
    
    override val isSinglePrecision: Boolean = true
    
    override fun get(row: Int, col: Int): Double = data[row][col].toDouble()
    
    override fun set(row: Int, col: Int, value: Double) {
        data[row][col] = value.toFloat()
    }
    
    override fun get(row: Int): DoubleArray = DoubleArray(cols) { data[row][it].toDouble() }
    
    override fun deepCopy(): FlexibleMatrix = FloatMatrix(rows, cols, data.map { it.copyOf() }.toTypedArray())
    
    override fun toDoubleMatrix(): Array<DoubleArray> = data.map { row ->
        DoubleArray(row.size) { row[it].toDouble() }
    }.toTypedArray()
    
    override fun toFloatMatrix(): Array<FloatArray> = data.map { it.copyOf() }.toTypedArray()
    
    /**
     * Gets the underlying FloatArray for efficient access
     */
    fun getFloatArray(row: Int): FloatArray = data[row]
}

// Factory functions for creating matrices
fun createDoubleMatrix(rows: Int, cols: Int, initializer: (Int, Int) -> Double = { _, _ -> 0.0 }): DoubleMatrix {
    val data = Array(rows) { row ->
        DoubleArray(cols) { col -> initializer(row, col) }
    }
    return DoubleMatrix(data)
}

fun createFloatMatrix(rows: Int, cols: Int, initializer: (Int, Int) -> Double = { _, _ -> 0.0 }): FloatMatrix {
    val data = Array(rows) { row ->
        FloatArray(cols) { col -> initializer(row, col).toFloat() }
    }
    return FloatMatrix(data)
}

/**
 * Creates a matrix with the specified precision
 */
fun createMatrix(rows: Int, cols: Int, useSinglePrecision: Boolean = false, initializer: (Int, Int) -> Double = { _, _ -> 0.0 }): FlexibleMatrix {
    return if (useSinglePrecision) {
        createFloatMatrix(rows, cols, initializer)
    } else {
        createDoubleMatrix(rows, cols, initializer)
    }
}

// Conversion functions
fun Array<FloatArray>.toFlexibleMatrix(): FloatMatrix = FloatMatrix(this)
fun Array<DoubleArray>.toFlexibleMatrix(): DoubleMatrix = DoubleMatrix(this)

// Extension functions removed - Matrix typealias eliminated to prevent forced double usage
