package com.onyxdevtools.ai.extensions

fun matrixMultiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val aRows = a.size
    val aCols = a[0].size
    val bRows = b.size
    val bCols = b[0].size
    require(aCols == bRows) { "Matrix dimensions mismatch for multiplication" }
    val result = Array(aRows) { DoubleArray(bCols) }
    for (i in 0 until aRows) for (j in 0 until bCols) for (k in 0 until aCols)
        result[i][j] += a[i][k] * b[k][j]
    return result
}

fun addVectorToRows(matrix: Array<DoubleArray>, vector: DoubleArray) =
    matrix.map { row -> row.zip(vector).map { (r, v) -> r + v }.toDoubleArray() }.toTypedArray()

fun applyElementWise(matrix: Array<DoubleArray>, func: (Double) -> Double) =
    matrix.map { row -> row.map(func).toDoubleArray() }.toTypedArray()

fun elementWiseMultiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    require(a.size == b.size && a[0].size == b[0].size) { "Matrix dimensions must match" }
    return a.mapIndexed { i, row -> row.mapIndexed { j, v -> v * b[i][j] }.toDoubleArray() }.toTypedArray()
}

fun transpose(m: Array<DoubleArray>): Array<DoubleArray> {
    val r = m.size
    val c = m[0].size
    return Array(c) { j -> DoubleArray(r) { i -> m[i][j] } }
}

fun sumColumns(m: Array<DoubleArray>): DoubleArray =
    DoubleArray(if (m.isEmpty()) 0 else m[0].size) { j -> m.sumOf { it.getOrElse(j) { 0.0 } } }

fun subtract(a: Array<DoubleArray>, b: Array<DoubleArray>) =
    a.mapIndexed { i, row -> row.mapIndexed { j, v -> v - b[i][j] }.toDoubleArray() }.toTypedArray()

fun scalarMultiply(m: Array<DoubleArray>, s: Double) =
    m.map { row -> row.map { it * s }.toDoubleArray() }.toTypedArray()

fun add(a: Array<DoubleArray>, b: Array<DoubleArray>) =
    a.mapIndexed { i, row -> row.mapIndexed { j, v -> v + b[i][j] }.toDoubleArray() }.toTypedArray()
