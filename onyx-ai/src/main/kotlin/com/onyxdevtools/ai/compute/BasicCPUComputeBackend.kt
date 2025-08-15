package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Basic CPU compute backend that works on all platforms including Android.
 * Tensor-native implementation (no Java Vector API required).
 */
open class BasicCPUComputeBackend : ComputeBackend {

    override val backendType: ComputeBackendType = ComputeBackendType.CPU

    // ---------- Core ops ----------

    override fun matrixMultiply(a: Tensor, b: Tensor): Tensor {
        require(a.cols == b.rows) {
            "Matrix dimensions don't match for multiplication: ${a.rows}x${a.cols} * ${b.rows}x${b.cols}"
        }
        return matrixMultiplyBasic(a, b)
    }

    override fun add(a: Tensor, b: Tensor): Tensor {
        require(a.rows == b.rows && a.cols == b.cols) {
            "Matrix dimensions don't match for addition: ${a.rows}x${a.cols} vs ${b.rows}x${b.cols}"
        }
        val m = a.rows
        val n = a.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = a[r, c] + b[r, c]
                c++
            }
            r++
        }
        return out
    }

    override fun subtract(a: Tensor, b: Tensor): Tensor {
        require(a.rows == b.rows && a.cols == b.cols) {
            "Matrix dimensions don't match for subtraction: ${a.rows}x${a.cols} vs ${b.rows}x${b.cols}"
        }
        val m = a.rows
        val n = a.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = a[r, c] - b[r, c]
                c++
            }
            r++
        }
        return out
    }

    override fun elementWiseMultiply(a: Tensor, b: Tensor): Tensor {
        require(a.rows == b.rows && a.cols == b.cols) {
            "Matrix dimensions don't match for element-wise multiply: ${a.rows}x${a.cols} vs ${b.rows}x${b.cols}"
        }
        val m = a.rows
        val n = a.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = a[r, c] * b[r, c]
                c++
            }
            r++
        }
        return out
    }

    override fun transpose(tensor: Tensor): Tensor {
        val m = tensor.rows
        val n = tensor.cols
        val out = createTensor(n, m)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[c, r] = tensor[r, c]
                c++
            }
            r++
        }
        return out
    }

    override fun scalarMultiply(tensor: Tensor, scalar: Float): Tensor {
        val m = tensor.rows
        val n = tensor.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = tensor[r, c] * scalar
                c++
            }
            r++
        }
        return out
    }

    override fun addVectorToRows(tensor: Tensor, vector: FloatArray): Tensor {
        require(vector.size == tensor.cols) {
            "Vector length ${vector.size} must equal tensor.cols ${tensor.cols}"
        }
        val m = tensor.rows
        val n = tensor.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = tensor[r, c] + vector[c]
                c++
            }
            r++
        }
        return out
    }

    override fun applyElementWise(tensor: Tensor, transform: (Float) -> Float): Tensor {
        val m = tensor.rows
        val n = tensor.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = transform(tensor[r, c])
                c++
            }
            r++
        }
        return out
    }

    override fun sumColumns(tensor: Tensor): FloatArray {
        val m = tensor.rows
        val n = tensor.cols
        if (m == 0 || n == 0) return FloatArray(0)
        val sums = FloatArray(n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                sums[c] += tensor[r, c]
                c++
            }
            r++
        }
        return sums
    }

    override fun softmax(tensor: Tensor): Tensor {
        val m = tensor.rows
        val n = tensor.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            // find max for stability
            var maxVal = tensor[r, 0]
            var c = 1
            while (c < n) {
                val v = tensor[r, c]
                if (v > maxVal) maxVal = v
                c++
            }
            // exp and sum
            var sum = 0f
            c = 0
            while (c < n) {
                val e = exp((tensor[r, c] - maxVal).toDouble()).toFloat()
                out[r, c] = e
                sum += e
                c++
            }
            val inv = if (sum != 0f) 1f / sum else 0f
            c = 0
            while (c < n) {
                out[r, c] *= inv
                c++
            }
            r++
        }
        return out
    }

    override fun meanStandardError(predicted: Tensor, actual: Tensor): Float {
        val m = minOf(predicted.rows, actual.rows)
        val n = if (m == 0) 0 else minOf(predicted.cols, actual.cols)
        if (m == 0 || n == 0) return 0f

        var sum = 0f
        var count = 0
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                val d = predicted[r, c] - actual[r, c]
                sum += d * d
                count++
                c++
            }
            r++
        }
        return if (count > 0) sum / count else 0f
    }

    override fun deepCopy(tensor: Tensor): Tensor {
        val m = tensor.rows
        val n = tensor.cols
        val out = createTensor(m, n)
        var r = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[r, c] = tensor[r, c]
                c++
            }
            r++
        }
        return out
    }

    override fun flatten(tensor: Tensor): FloatArray {
        val m = tensor.rows
        val n = tensor.cols
        val out = FloatArray(m * n)
        var r = 0
        var dst = 0
        while (r < m) {
            var c = 0
            while (c < n) {
                out[dst++] = tensor[r, c]
                c++
            }
            r++
        }
        return out
    }

    // ---------- Basic GEMM used by matrixMultiply ----------

    internal fun matrixMultiplyBasic(a: Tensor, b: Tensor): Tensor {
        val m = a.rows
        val k = a.cols
        val n = b.cols
        val out = createTensor(m, n)

        var r = 0
        while (r < m) {
            var p = 0
            while (p < k) {
                val aVal = a[r, p]
                // read B row p across all columns
                var c = 0
                while (c < n) {
                    out[r, c] = out[r, c] + aVal * b[p, c]
                    c++
                }
                p++
            }
            r++
        }
        return out
    }
}
