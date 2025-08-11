package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.extensions.EPSILON
import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable

/* ───────────────── P² online quantile estimator ──────────────── */
private class P2(private val p: Float) : Serializable {
    private val q  = FloatArray(5)     // marker heights
    private val n  = IntArray(5)        // positions
    private val ns = FloatArray(5)     // desired positions
    private val d  = floatArrayOf(0.0f, p / 2, p, (1 + p) / 2, 1.0f)
    private var count = 0

    fun observe(x: Float) {
        if (count < 5) {                        // initial fill
            q[count] = x
            count++
            if (count == 5) {
                q.sort()
                for (i in 0..4) n[i] = i
                for (i in 0..4) ns[i] = d[i] * 4
            }
            return
        }
        val k = when {
            x < q[0] -> { q[0] = x; 0 }
            x < q[1] -> 0
            x < q[2] -> 1
            x < q[3] -> 2
            x < q[4] -> 3
            else -> { q[4] = x; 3 }
        }
        for (i in k + 1..4) n[i]++
        for (i in 0..4) ns[i] += d[i]
        for (i in 1..3) {
            val dn = ns[i] - n[i]
            if ((dn >= 1 && n[i + 1] - n[i] > 1) ||
                (dn <= -1 && n[i] - n[i - 1] > 1)) {
                val sign = dn.sign
                val qp = parabolic(i, sign)
                if (q[i - 1] < qp && qp < q[i + 1])
                    q[i] = qp
                else
                    q[i] = linear(i, sign)
                n[i] += sign
            }
        }
    }

    fun value(): Float = when {
        count < 5 -> q.copyOf(count).sorted()[ (count - 1) / 2 ]
        else -> q[2]
    }

    private fun parabolic(i: Int, d: Int): Float =
        q[i] + d / (n[i + 1] - n[i - 1]).toFloat() *
                ((n[i] - n[i - 1] + d) * (q[i + 1] - q[i]) /
                        (n[i + 1] - n[i]) +
                        (n[i + 1] - n[i] - d) * (q[i] - q[i - 1]) /
                        (n[i] - n[i - 1]))

    private fun linear(i: Int, d: Int): Float =
        q[i] + d * (q[i + d] - q[i - d]) / (n[i + d] - n[i - d])

    private val Float.sign get() = when { this > 0 -> 1; this < 0 -> -1; else -> 0 }

    /**
     * Clone this P2 estimator with all its internal state.
     */
    fun clone(): P2 = P2(p).also { cp ->
        System.arraycopy(this.q, 0, cp.q, 0, this.q.size)
        System.arraycopy(this.n, 0, cp.n, 0, this.n.size)
        System.arraycopy(this.ns, 0, cp.ns, 0, this.ns.size)
        cp.count = this.count
    }

    /**
     * Copy state from another P2 into this one.
     */
    fun copyFrom(other: P2) {
        System.arraycopy(other.q, 0, this.q, 0, this.q.size)
        System.arraycopy(other.n, 0, this.n, 0, this.n.size)
        System.arraycopy(other.ns, 0, this.ns, 0, this.ns.size)
        this.count = other.count
    }
}

/* ───────────────── RobustScaler ──────────────────────────────── */
class RobustScaler(
    private val exactUntil: Int = 20        // threshold for exact mode
) : ColumnTransform, Serializable {

    /* running exact buffer (small) */
    private val buffer = mutableListOf<Float>()

    /* P² estimators once buffer > exactUntil */
    private val q25 = P2(0.25f)
    private val q50 = P2(0.50f)
    private val q75 = P2(0.75f)
    private var useP2  = false

    private var median = 0.0f
    private var iqr    = 1.0f
    private var fitted = false

    override fun isFitted(): Boolean = fitted

    override fun fit(values: FloatArray) {
        if (!useP2) {                       // still in exact mode
            buffer += values.asList()
            if (buffer.size <= exactUntil) {
                updateExact()
                return
            }
            /* switch to P2: seed estimators with buffered points */
            buffer.forEach {
                q25.observe(it); q50.observe(it); q75.observe(it)
            }
            buffer.clear()
            useP2 = true
        }
        /* P2 mode */
        values.forEach {
            q25.observe(it); q50.observe(it); q75.observe(it)
        }
        updateStats()
    }

    private fun updateExact() {
        if (buffer.isEmpty()) return
        val sorted = buffer.sorted()
        median = percentile(sorted, 0.5f)
        val q1 = percentile(sorted, 0.25f)
        val q3 = percentile(sorted, 0.75f)
        iqr = (q3 - q1).coerceAtLeast(EPSILON.toFloat())
        fitted = true
    }

    private fun updateStats() {
        median = q50.value()
        iqr = (q75.value() - q25.value()).coerceAtLeast(EPSILON.toFloat())
        fitted = true
    }

    override fun apply(values: FloatArray): FloatArray {
        if (!fitted) fit(values)
        return FloatArray(values.size) { i -> (values[i] - median) / iqr }
    }

    override fun inverse(values: FloatArray): FloatArray {
        if (!fitted) throw IllegalStateException("inverse() before fit/apply")
        return FloatArray(values.size) { i -> median + values[i] * iqr }
    }

    /* exact percentile helper */
    private fun percentile(sorted: List<Float>, p: Float): Float {
        val n = sorted.size
        if (n == 1) return sorted[0]
        val pos = p * (n - 1)
        val lo = pos.toInt(); val hi = (lo + 1).coerceAtMost(n - 1)
        val frac = pos - lo
        return sorted[lo] * (1 - frac) + sorted[hi] * frac
    }

    /**
     * Clone this scaler and all its state so that mutating one copy doesn't
     * affect the other.
     */
    override fun clone(): ColumnTransform = RobustScaler(exactUntil).also { copy ->
        copy.useP2  = this.useP2
        copy.buffer += this.buffer
        copy.q25.copyFrom(this.q25)
        copy.q50.copyFrom(this.q50)
        copy.q75.copyFrom(this.q75)
        copy.median = this.median
        copy.iqr    = this.iqr
        copy.fitted = this.fitted
    }
}
