package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.exception.OnyxException
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.annotations.values.VectorQuantization
import com.onyx.persistence.context.SchemaContext
import java.lang.ref.WeakReference
import java.util.Random
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * HNSW Vector Index Interactor (drop-in replacement).
 *
 * Fixes graph quality by:
 *  - bounded degree per layer (M / 2M)
 *  - ALWAYS bidirectional linking
 *  - pruning neighbor lists back to degree cap using the same similarity as query scoring
 *  - matchAll uses maxCandidates as efSearch (not "limit * searchRadius * 10")
 *
 * Configuration is read from the IndexDescriptor which is populated from the @Index annotation:
 *  - maxNeighbors: number of bi-directional links per node per layer (default: 16)
 *  - searchRadius: search width during index construction (default: 128)
 *  - quantization: vector quantization mode - NONE, INT8, or INT4 (default: NONE)
 *  - minimumScore: minimum cosine similarity score for results (default: -1f = disabled)
 *  - embeddingDimensions: expected vector dimensions (default: -1 = any)
 */
class VectorIndexInteractor @Throws(OnyxException::class) constructor(
    private val entityDescriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : IndexInteractor {

    private val lock = ReentrantReadWriteLock()
    private val contextRef = WeakReference(context)
    private val context: SchemaContext get() = contextRef.get() ?: throw IllegalStateException("Context GC'd")

    // HNSW configuration - read from descriptor (set via @Index annotation)
    private val M: Int = indexDescriptor.maxNeighbors
    private val searchRadius: Int = indexDescriptor.searchRadius
    private val quantization: VectorQuantization = indexDescriptor.quantization
    private val minimumScore: Float = indexDescriptor.minimumScore
    private val embeddingDimensions: Int = indexDescriptor.embeddingDimensions
    private val mL = 1.0 / ln(M.toDouble())
    private val random = Random()

    private var entryNodeId: Long = -1L
    private var maxLayer: Int = -1

    /** Float store (legacy / NONE mode) */
    private val vectorStoreF: DiskMap<Long, FloatArray>
        get() = context.getDataFile(entityDescriptor).getHashMap(
            Long::class.java, "${entityDescriptor.entityClass.name}${indexDescriptor.name}_vectors"
        )

    /** INT8 store */
    private val vectorStoreQ8: DiskMap<Long, ByteArray>
        get() = context.getDataFile(entityDescriptor).getHashMap(
            Long::class.java, "${entityDescriptor.entityClass.name}${indexDescriptor.name}_vectors_q8"
        )

    /** INT4 store (packed 2 per byte) */
    private val vectorStoreQ4: DiskMap<Long, ByteArray>
        get() = context.getDataFile(entityDescriptor).getHashMap(
            Long::class.java, "${entityDescriptor.entityClass.name}${indexDescriptor.name}_vectors_q4"
        )

    /** Adjacency per layer */
    private fun getGraphLayer(layer: Int): DiskMap<Long, LongArray> =
        context.getDataFile(entityDescriptor).getHashMap(
            Long::class.java, "${entityDescriptor.entityClass.name}${indexDescriptor.name}_hnsw_layer_$layer"
        )

    // ---------------------------
    // Caches
    // ---------------------------

    private val vectorCacheF = WeakHashMap<Long, FloatArray>()
    private val vectorCacheQ8 = WeakHashMap<Long, ByteArray>()
    private val vectorCacheQ4 = WeakHashMap<Long, ByteArray>()
    private val neighborCache = WeakHashMap<Long, LongArray>()

    private fun neighborCacheKey(layer: Int, nodeId: Long): Long =
        (nodeId shl 8) xor (layer.toLong() and 0xffL)

    private fun getVectorF_Cached(id: Long): FloatArray? {
        vectorCacheF[id]?.let { return it }
        val v = vectorStoreF[id] ?: return null
        vectorCacheF[id] = v
        return v
    }

    private fun getVectorQ8_Cached(id: Long): ByteArray? {
        vectorCacheQ8[id]?.let { return it }
        val v = vectorStoreQ8[id] ?: return null
        vectorCacheQ8[id] = v
        return v
    }

    private fun getVectorQ4_Cached(id: Long): ByteArray? {
        vectorCacheQ4[id]?.let { return it }
        val v = vectorStoreQ4[id] ?: return null
        vectorCacheQ4[id] = v
        return v
    }

    private fun getNeighborsCached(layer: Int, layerMap: DiskMap<Long, LongArray>, id: Long): LongArray? {
        val key = neighborCacheKey(layer, id)
        neighborCache[key]?.let { return it }
        val n = layerMap[id] ?: return null
        neighborCache[key] = n
        return n
    }

    private fun invalidateVector(id: Long) {
        vectorCacheF.remove(id)
        vectorCacheQ8.remove(id)
        vectorCacheQ4.remove(id)
    }

    private fun invalidateNeighborsAllLayers(@Suppress("UNUSED_PARAMETER") id: Long) {
        neighborCache.clear()
    }

    // -----------------------------------------
    // Packed heaps + visited set
    // -----------------------------------------

    private fun pack(id: Long, score: Float): Long =
        ((id.toInt().toLong() and 0xffffffffL) shl 32) or (score.toBits().toLong() and 0xffffffffL)

    private fun unpackId(packed: Long): Long = (packed ushr 32) and 0xffffffffL
    private fun unpackScore(packed: Long): Float = Float.fromBits((packed and 0xffffffffL).toInt())

    private class LongIdSet(capacity: Int) {
        private val empty = 0xffffffffL
        private var mask: Int
        private var table: LongArray
        private var size = 0
        private var threshold: Int

        init {
            var cap = 1
            while (cap < capacity * 2) cap = cap shl 1
            table = LongArray(cap) { empty }
            mask = cap - 1
            threshold = (cap * 0.7f).toInt()
        }

        fun add(id: Long): Boolean {
            val key = id and 0xffffffffL
            var idx = mix32(key.toInt()) and mask
            while (true) {
                val cur = table[idx]
                if (cur == empty) {
                    table[idx] = key
                    if (++size >= threshold) rehash()
                    return true
                }
                if (cur == key) return false
                idx = (idx + 1) and mask
            }
        }

        private fun rehash() {
            val old = table
            val newCap = old.size shl 1
            table = LongArray(newCap) { empty }
            mask = newCap - 1
            threshold = (newCap * 0.7f).toInt()
            size = 0
            for (v in old) {
                if (v != empty) {
                    var idx = mix32(v.toInt()) and mask
                    while (table[idx] != empty) idx = (idx + 1) and mask
                    table[idx] = v
                    size++
                }
            }
        }

        private fun mix32(x0: Int): Int {
            var x = x0
            x = x xor (x ushr 16)
            x *= -0x7a143595
            x = x xor (x ushr 15)
            x *= -0x3d4d51cb
            x = x xor (x ushr 16)
            return x
        }
    }

    private class PackedMaxHeap(initialCapacity: Int = 64) {
        private var a = LongArray(maxOf(4, initialCapacity))
        private var size = 0

        fun isEmpty(): Boolean = size == 0

        fun add(p: Long) {
            if (size == a.size) a = a.copyOf(a.size shl 1)
            var i = size++
            a[i] = p
            while (i > 0) {
                val parent = (i - 1) ushr 1
                if (unpackScore(a[parent]) >= unpackScore(a[i])) break
                val tmp = a[parent]; a[parent] = a[i]; a[i] = tmp
                i = parent
            }
        }

        fun poll(): Long {
            val res = a[0]
            val last = a[--size]
            if (size > 0) {
                a[0] = last
                heapifyDown(0)
            }
            return res
        }

        private fun heapifyDown(i0: Int) {
            var i = i0
            while (true) {
                val l = (i shl 1) + 1
                if (l >= size) return
                val r = l + 1
                var best = l
                if (r < size && unpackScore(a[r]) > unpackScore(a[l])) best = r
                if (unpackScore(a[i]) >= unpackScore(a[best])) return
                val tmp = a[i]; a[i] = a[best]; a[best] = tmp
                i = best
            }
        }

        companion object {
            private fun unpackScore(packed: Long): Float = Float.fromBits((packed and 0xffffffffL).toInt())
        }
    }

    private class PackedMinHeap(initialCapacity: Int = 64) {
        private var a = LongArray(maxOf(4, initialCapacity))
        private var size = 0

        fun size(): Int = size
        fun isEmpty(): Boolean = size == 0
        fun peek(): Long = a[0]

        fun add(p: Long) {
            if (size == a.size) a = a.copyOf(a.size shl 1)
            var i = size++
            a[i] = p
            while (i > 0) {
                val parent = (i - 1) ushr 1
                if (unpackScore(a[parent]) <= unpackScore(a[i])) break
                val tmp = a[parent]; a[parent] = a[i]; a[i] = tmp
                i = parent
            }
        }

        fun poll(): Long {
            val res = a[0]
            val last = a[--size]
            if (size > 0) {
                a[0] = last
                heapifyDown(0)
            }
            return res
        }

        fun toDescendingPairs(): List<Pair<Long, Float>> {
            val out = ArrayList<Pair<Long, Float>>(size)
            while (!isEmpty()) {
                val p = poll()
                out.add(unpackId(p).toLong() to unpackScore(p))
            }
            out.reverse()
            return out
        }

        private fun heapifyDown(i0: Int) {
            var i = i0
            while (true) {
                val l = (i shl 1) + 1
                if (l >= size) return
                val r = l + 1
                var best = l
                if (r < size && unpackScore(a[r]) < unpackScore(a[l])) best = r
                if (unpackScore(a[i]) <= unpackScore(a[best])) return
                val tmp = a[i]; a[i] = a[best]; a[best] = tmp
                i = best
            }
        }

        companion object {
            private fun unpackId(packed: Long): Int = ((packed ushr 32) and 0xffffffffL).toInt()
            private fun unpackScore(packed: Long): Float = Float.fromBits((packed and 0xffffffffL).toInt())
        }
    }

    // ---------------------------
    // Quantization
    // ---------------------------

    private fun clampUnit(x0: Float): Float {
        var x = x0
        if (x > 1f) x = 1f
        else if (x < -1f) x = -1f
        return x
    }

    private val q8Scale = 127f
    private val q8InvScale2 = 1f / (q8Scale * q8Scale)

    private fun quantizeInt8(v: FloatArray): ByteArray {
        val out = ByteArray(v.size)
        var i = 0
        while (i < v.size) {
            val x = clampUnit(v[i])
            val qi = (x * q8Scale).roundToInt().coerceIn(-127, 127)
            out[i] = qi.toByte()
            i++
        }
        return out
    }

    private fun dotQ8Q8(a: ByteArray, b: ByteArray): Float {
        var sum = 0
        var i = 0
        while (i < a.size) {
            sum += a[i].toInt() * b[i].toInt()
            i++
        }
        return sum.toFloat() * q8InvScale2
    }

    private val q4Scale = 7f
    private val q4InvScale2 = 1f / (q4Scale * q4Scale)

    private fun packNibbleSigned7(x: Int): Int = (x + 8) and 0xF
    private fun unpackNibbleSigned7(n: Int): Int = (n and 0xF) - 8

    private fun quantizeInt4Packed(v: FloatArray): ByteArray {
        val n = v.size
        val out = ByteArray((n + 1) ushr 1)
        var i = 0
        var oi = 0
        while (i < n) {
            val x0 = clampUnit(v[i])
            val q0 = (x0 * q4Scale).roundToInt().coerceIn(-7, 7)
            val lo = packNibbleSigned7(q0)

            val hi = if (i + 1 < n) {
                val x1 = clampUnit(v[i + 1])
                val q1 = (x1 * q4Scale).roundToInt().coerceIn(-7, 7)
                packNibbleSigned7(q1)
            } else {
                8 // pad
            }

            out[oi++] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }

    private fun dotQ4Q4Packed(a: ByteArray, b: ByteArray): Float {
        var sum = 0
        var i = 0
        while (i < a.size) {
            val ab = a[i].toInt() and 0xFF
            val bb = b[i].toInt() and 0xFF

            val aLo = unpackNibbleSigned7(ab and 0x0F)
            val bLo = unpackNibbleSigned7(bb and 0x0F)
            sum += aLo * bLo

            val aHi = unpackNibbleSigned7(ab ushr 4)
            val bHi = unpackNibbleSigned7(bb ushr 4)
            sum += aHi * bHi

            i++
        }
        return sum.toFloat() * q4InvScale2
    }

    private class PreparedQuery(
        val f32: FloatArray,
        val q8: ByteArray?,
        val q4: ByteArray?
    )

    private fun prepareQuery(q: FloatArray): PreparedQuery = when (quantization) {
        VectorQuantization.NONE -> PreparedQuery(q, null, null)
        VectorQuantization.INT8 -> PreparedQuery(q, quantizeInt8(q), null)
        VectorQuantization.INT4 -> PreparedQuery(q, null, quantizeInt4Packed(q))
    }

    private fun score(prepared: PreparedQuery, nodeId: Long): Float? {
        return when (quantization) {
            VectorQuantization.NONE -> {
                val v = getVectorF_Cached(nodeId) ?: return null
                if (v.size != prepared.f32.size) return null
                dotProduct(prepared.f32, v)
            }

            VectorQuantization.INT8 -> {
                var vq = getVectorQ8_Cached(nodeId)
                if (vq == null) {
                    val vf = getVectorF_Cached(nodeId) ?: return null
                    if (vf.size != prepared.f32.size) return null
                    vq = quantizeInt8(vf)
                    vectorStoreQ8[nodeId] = vq
                    vectorCacheQ8[nodeId] = vq
                }
                if (vq.size != prepared.q8!!.size) return null
                dotQ8Q8(prepared.q8, vq)
            }

            VectorQuantization.INT4 -> {
                var vq = getVectorQ4_Cached(nodeId)
                if (vq == null) {
                    val vf = getVectorF_Cached(nodeId) ?: return null
                    if (vf.size != prepared.f32.size) return null
                    vq = quantizeInt4Packed(vf)
                    vectorStoreQ4[nodeId] = vq
                    vectorCacheQ4[nodeId] = vq
                }
                if (vq.size != prepared.q4!!.size) return null
                dotQ4Q4Packed(prepared.q4, vq)
            }
        }
    }

    // ---------------------------
    // Graph quality helpers (NEW)
    // ---------------------------

    private fun maxDegreeForLayer(layer: Int): Int = if (layer == 0) M * 2 else M
    private fun expectedQ4Len(dim: Int): Int = (dim + 1) ushr 1
    private fun overflowCapForLayer(layer: Int): Int = maxDegreeForLayer(layer) * 2

    private fun getVectorQ8_OrBackfill(id: Long, dim: Int): ByteArray? {
        getVectorQ8_Cached(id)?.let { return if (it.size == dim) it else null }
        val vf = getVectorF_Cached(id) ?: return null
        if (vf.size != dim) return null
        val q = quantizeInt8(vf)
        vectorStoreQ8[id] = q
        vectorCacheQ8[id] = q
        return q
    }

    private fun getVectorQ4_OrBackfill(id: Long, dim: Int): ByteArray? {
        getVectorQ4_Cached(id)?.let { return if (it.size == expectedQ4Len(dim)) it else null }
        val vf = getVectorF_Cached(id) ?: return null
        if (vf.size != dim) return null
        val q = quantizeInt4Packed(vf)
        vectorStoreQ4[id] = q
        vectorCacheQ4[id] = q
        return q
    }

    private fun scoreBetween(aId: Long, bId: Long, dim: Int): Float? {
        return when (quantization) {
            VectorQuantization.NONE -> {
                val a = getVectorF_Cached(aId) ?: return null
                val b = getVectorF_Cached(bId) ?: return null
                if (a.size != dim || b.size != dim) return null
                dotProduct(a, b)
            }

            VectorQuantization.INT8 -> {
                val a = getVectorQ8_OrBackfill(aId, dim) ?: return null
                val b = getVectorQ8_OrBackfill(bId, dim) ?: return null
                if (a.size != b.size) return null
                dotQ8Q8(a, b)
            }

            VectorQuantization.INT4 -> {
                val a = getVectorQ4_OrBackfill(aId, dim) ?: return null
                val b = getVectorQ4_OrBackfill(bId, dim) ?: return null
                if (a.size != b.size) return null
                dotQ4Q4Packed(a, b)
            }
        }
    }

    private fun appendNeighbor(existing: LongArray, id: Long): LongArray {
        val n = existing.size
        val out = existing.copyOf(n + 1)
        out[n] = id
        return out
    }

    private fun pruneBySimilarity(
        centerId: Long,
        candidates: LongArray,
        maxDegree: Int,
        dim: Int,
        mustKeep: Long? = null
    ): LongArray {
        if (candidates.isEmpty()) return longArrayOf()
        if (candidates.size <= maxDegree && mustKeep == null) return candidates

        // candidates are small (<= overflow cap), so use O(k^2) dedupe to avoid HashSet allocations.
        val tmp = LongArray(candidates.size + if (mustKeep != null) 1 else 0)
        var tmpN = 0

        fun addUnique(id: Long) {
            if (id == centerId) return
            for (i in 0 until tmpN) if (tmp[i] == id) return
            tmp[tmpN++] = id
        }

        for (id in candidates) addUnique(id)
        if (mustKeep != null) addUnique(mustKeep)

        // Score them
        val ids = LongArray(tmpN)
        val scores = FloatArray(tmpN)
        var n = 0
        var mustKeepScore: Float? = null

        for (i in 0 until tmpN) {
            val id = tmp[i]
            val s = scoreBetween(centerId, id, dim) ?: continue
            ids[n] = id
            scores[n] = s
            if (mustKeep != null && id == mustKeep) mustKeepScore = s
            n++
        }

        if (n == 0) return longArrayOf()
        if (n <= maxDegree) {
            // Ensure mustKeep if requested and available
            if (mustKeep != null && mustKeepScore != null) {
                var found = false
                for (i in 0 until n) if (ids[i] == mustKeep) {
                    found = true; break
                }
                if (!found) {
                    // append if room, else replace last
                    return if (n < maxDegree) ids.copyOf(n + 1).also { it[n] = mustKeep }
                    else ids.also { it[n - 1] = mustKeep }
                }
            }
            return ids.copyOf(n)
        }

        // Keep top maxDegree by score using insertion into a small sorted buffer (no full sort).
        val outIds = LongArray(maxDegree)
        val outScores = FloatArray(maxDegree)
        var outN = 0

        fun insertSorted(id: Long, s: Float) {
            if (outN < maxDegree) {
                var j = outN
                outN++
                while (j > 0 && outScores[j - 1] < s) {
                    outScores[j] = outScores[j - 1]
                    outIds[j] = outIds[j - 1]
                    j--
                }
                outScores[j] = s
                outIds[j] = id
            } else {
                // full: only insert if better than worst
                if (s <= outScores[maxDegree - 1]) return
                var j = maxDegree - 1
                while (j > 0 && outScores[j - 1] < s) {
                    outScores[j] = outScores[j - 1]
                    outIds[j] = outIds[j - 1]
                    j--
                }
                outScores[j] = s
                outIds[j] = id
            }
        }

        for (i in 0 until n) insertSorted(ids[i], scores[i])

        // Force mustKeep if requested and it had a score
        val mk = mustKeep
        if (mk != null && mustKeepScore != null) {
            var found = false
            for (i in 0 until outN) if (outIds[i] == mk) {
                found = true; break
            }
            if (!found && outN > 0) outIds[outN - 1] = mk
        }

        return if (outN == maxDegree) outIds else outIds.copyOf(outN)
    }

    private fun addBidirectionalLink(
        layerMap: DiskMap<Long, LongArray>,
        a: Long,
        b: Long,
        layer: Int,
        dim: Int
    ) {
        val deg = maxDegreeForLayer(layer)
        val overflowCap = overflowCapForLayer(layer)

        // a -> b
        run {
            val existing = layerMap[a] ?: longArrayOf()
            for (x in existing) if (x == b) return@run
            val appended = appendNeighbor(existing, b)

            layerMap[a] = if (appended.size <= overflowCap) {
                appended
            } else {
                pruneBySimilarity(a, appended, deg, dim, mustKeep = b)
            }
        }

        // b -> a
        run {
            val existing = layerMap[b] ?: longArrayOf()
            for (x in existing) if (x == a) return@run
            val appended = appendNeighbor(existing, a)

            layerMap[b] = if (appended.size <= overflowCap) {
                appended
            } else {
                pruneBySimilarity(b, appended, deg, dim, mustKeep = a)
            }
        }
    }

    // ---------------------------
    // Core operations
    // ---------------------------

    @Throws(OnyxException::class)
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) = lock.write {
        if (oldReferenceId > 0) delete(oldReferenceId)

        val vector = valueToVector(indexValue) ?: return@write
        normalize(vector)

        // Persist vector
        when (quantization) {
            VectorQuantization.NONE -> {
                vectorStoreF[newReferenceId] = vector
                vectorStoreQ8.remove(newReferenceId)
                vectorStoreQ4.remove(newReferenceId)
            }

            VectorQuantization.INT8 -> {
                vectorStoreQ8[newReferenceId] = quantizeInt8(vector)
                vectorStoreQ4.remove(newReferenceId)
                vectorStoreF.remove(newReferenceId)
            }

            VectorQuantization.INT4 -> {
                vectorStoreQ4[newReferenceId] = quantizeInt4Packed(vector)
                vectorStoreQ8.remove(newReferenceId)
                vectorStoreF.remove(newReferenceId)
            }
        }

        invalidateVector(newReferenceId)
        invalidateNeighborsAllLayers(newReferenceId)

        val targetLayer = ((-ln(random.nextDouble()) * mL)).toInt()

        // First node
        if (entryNodeId == -1L) {
            entryNodeId = newReferenceId
            maxLayer = targetLayer
            for (l in 0..targetLayer) getGraphLayer(l)[newReferenceId] = longArrayOf()
            neighborCache.clear()
            return@write
        }

        // If this node increases maxLayer, ensure upper layer adjacency exists
        if (targetLayer > maxLayer) {
            for (l in (maxLayer + 1)..targetLayer) {
                getGraphLayer(l)[newReferenceId] = longArrayOf()
            }
        }

        val prepared = prepareQuery(vector)
        var currEntryPoint = entryNodeId
        val dim = prepared.f32.size

        // Greedy descent above target layer
        for (l in maxLayer downTo targetLayer + 1) {
            val best = searchLayerPacked(prepared, currEntryPoint, 1, l)
            if (best.isNotEmpty()) currEntryPoint = best[0].first
        }

        // Link from min(level, maxLayer) down to 0
        for (l in minOf(targetLayer, maxLayer) downTo 0) {
            val layerMap = getGraphLayer(l)

            val neighbors = searchLayerPacked(prepared, currEntryPoint, searchRadius, l)

            val deg = maxDegreeForLayer(l)
            val picked = neighbors.take(deg).map { it.first }.toLongArray()

            // set adjacency for new node (bounded)
            layerMap[newReferenceId] = picked

            // always add reciprocal links + prune both sides
            for (nb in picked) {
                addBidirectionalLink(layerMap, newReferenceId, nb, l, dim)
            }

            neighborCache.clear()
            currEntryPoint = picked.firstOrNull() ?: currEntryPoint
        }

        // Update entry if highest level
        if (targetLayer > maxLayer) {
            maxLayer = targetLayer
            entryNodeId = newReferenceId
        }
    }

    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> = lock.read {
        val query = valueToVector(indexValue) ?: return@read emptyMap()
        normalize(query)

        if (entryNodeId == -1L) return@read emptyMap()

        val prepared = prepareQuery(query)
        var currEntryPoint = entryNodeId

        for (l in maxLayer downTo 1) {
            val best = searchLayerPacked(prepared, currEntryPoint, 1, l)
            if (best.isNotEmpty()) currEntryPoint = best[0].first
        }

        val efSearch = minOf(limit * searchRadius, maxCandidates)
        val results = searchLayerPacked(prepared, currEntryPoint, efSearch, 0)

        // Filter by minimumScore if specified (> 0)
        val filtered = if (minimumScore > 0f) {
            results.filter { it.second >= minimumScore }
        } else {
            results
        }

        return@read filtered.take(limit).associate { it.first to it.second }
    }


    private fun searchLayerPacked(prepared: PreparedQuery, entry: Long, ef: Int, layer: Int): List<Pair<Long, Float>> {
        val layerMap = getGraphLayer(layer)

        val visited = LongIdSet(capacity = ef * 8 + 16)
        val candidates = PackedMaxHeap(initialCapacity = ef * 2 + 16)
        val found = PackedMinHeap(initialCapacity = ef + 16)

        val entryScore = score(prepared, entry) ?: return emptyList()

        visited.add(entry)
        candidates.add(pack(entry, entryScore))
        found.add(pack(entry, entryScore))

        while (!candidates.isEmpty()) {
            val curr = candidates.poll()
            if (found.size() >= ef) {
                val worstKeptScore = unpackScore(found.peek())
                if (unpackScore(curr) <= worstKeptScore) break
            }

            val currId = unpackId(curr).toLong()
            val neighbors = getNeighborsCached(layer, layerMap, currId) ?: continue

            for (neighborId in neighbors) {
                if (!visited.add(neighborId)) continue

                val s = score(prepared, neighborId) ?: continue
                val packed = pack(neighborId, s)

                if (found.size() < ef) {
                    candidates.add(packed)
                    found.add(packed)
                } else {
                    val worstKeptScore = unpackScore(found.peek())
                    if (s > worstKeptScore) {
                        candidates.add(packed)
                        found.poll()
                        found.add(packed)
                    }
                }
            }
        }

        return found.toDescendingPairs()
    }

    // ---------------------------
    // Math / conversion
    // ---------------------------

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var res = 0f
        var i = 0
        val n = a.size
        while (i < n) {
            res += a[i] * b[i]
            i++
        }
        return res
    }

    private fun normalize(v: FloatArray) {
        val mag2 = dotProduct(v, v)
        if (mag2 <= 0f) return
        val inv = (1.0f / sqrt(mag2))
        var i = 0
        while (i < v.size) {
            v[i] *= inv
            i++
        }
    }

    private fun valueToVector(value: Any?): FloatArray? {
        val vector = when (value) {
            is FloatArray -> value.copyOf()
            is List<*> -> FloatArray(value.size) { (value[it] as Number).toFloat() }
            is Array<*> -> FloatArray(value.size) { (value[it] as Number).toFloat() }
            else -> return null
        }
        // Validate embeddingDimensions if specified
        if (embeddingDimensions > 0 && vector.size != embeddingDimensions) {
            return null // Vector dimension mismatch
        }
        return vector
    }

    // ---------------------------
    // Delete / rebuild / clear
    // ---------------------------

    override fun delete(reference: Long) = lock.write {
        vectorStoreF.remove(reference)
        vectorStoreQ8.remove(reference)
        vectorStoreQ4.remove(reference)

        detachFromGraph(reference)

        if (reference == entryNodeId) {
            val newEntry = pickAnyExistingNodeId()
            entryNodeId = newEntry ?: -1L
            if (entryNodeId == -1L) maxLayer = -1
        }

        invalidateVector(reference)
        invalidateNeighborsAllLayers(reference)
        Unit
    }

    override fun rebuild() = lock.write {
        val layerScanFallback = 128

        fun stableUnitDoubleFromId(id: Long): Double {
            val u = Random(id).nextDouble()
            return if (u <= 0.0) 1e-12 else u
        }

        fun layerForId(id: Long): Int {
            val u = stableUnitDoubleFromId(id)
            return ((-ln(u) * mL)).toInt()
        }

        fun dequantQ8ToFloat(q: ByteArray): FloatArray {
            val out = FloatArray(q.size)
            var i = 0
            while (i < q.size) {
                out[i] = q[i].toInt() / 127f
                i++
            }
            return out
        }

        fun dequantQ4ToFloat(q: ByteArray): FloatArray {
            val out = FloatArray(q.size * 2)
            var oi = 0
            var i = 0
            while (i < q.size) {
                val b = q[i].toInt() and 0xFF
                val lo = (b and 0x0F) - 8
                val hi = (b ushr 4) - 8
                out[oi++] = lo / 7f
                out[oi++] = hi / 7f
                i++
            }
            return out
        }

        fun loadAnyVectorAsFloat(id: Long): FloatArray? {
            vectorStoreF[id]?.let { return it.copyOf() }
            vectorStoreQ8[id]?.let { return dequantQ8ToFloat(it) }
            vectorStoreQ4[id]?.let { return dequantQ4ToFloat(it) }
            return null
        }

        fun persistInActiveFormat(id: Long, vNorm: FloatArray) {
            when (quantization) {
                VectorQuantization.NONE -> {
                    vectorStoreF[id] = vNorm
                    vectorStoreQ8.remove(id)
                    vectorStoreQ4.remove(id)
                    vectorCacheF[id] = vNorm
                    vectorCacheQ8.remove(id)
                    vectorCacheQ4.remove(id)
                }

                VectorQuantization.INT8 -> {
                    val q8 = quantizeInt8(vNorm)
                    vectorStoreQ8[id] = q8
                    vectorStoreF.remove(id)
                    vectorStoreQ4.remove(id)
                    vectorCacheQ8[id] = q8
                    vectorCacheF.remove(id)
                    vectorCacheQ4.remove(id)
                }

                VectorQuantization.INT4 -> {
                    val q4 = quantizeInt4Packed(vNorm)
                    vectorStoreQ4[id] = q4
                    vectorStoreF.remove(id)
                    vectorStoreQ8.remove(id)
                    vectorCacheQ4[id] = q4
                    vectorCacheF.remove(id)
                    vectorCacheQ8.remove(id)
                }
            }
        }

        // collect ids
        val ids = HashSet<Long>(1024)
        ids.addAll(vectorStoreF.keys)
        ids.addAll(vectorStoreQ8.keys)
        ids.addAll(vectorStoreQ4.keys)

        // wipe graph
        val upperToClear = maxOf(maxLayer, layerScanFallback)
        for (l in 0..upperToClear) getGraphLayer(l).clear()

        // reset
        entryNodeId = -1L
        maxLayer = -1
        neighborCache.clear()
        vectorCacheF.clear()
        vectorCacheQ8.clear()
        vectorCacheQ4.clear()

        if (ids.isEmpty()) return@write

        val sortedIds = ids.toLongArray().also { it.sort() }

        for (id in sortedIds) {
            val v = loadAnyVectorAsFloat(id) ?: continue
            normalize(v)
            persistInActiveFormat(id, v)

            val targetLayer = layerForId(id)
            val prepared = prepareQuery(v)
            val dim = prepared.f32.size

            if (entryNodeId == -1L) {
                entryNodeId = id
                maxLayer = targetLayer
                for (l in 0..targetLayer) getGraphLayer(l)[id] = longArrayOf()
                continue
            }

            // create empty adjacency in new upper layers if needed
            if (targetLayer > maxLayer) {
                for (l in (maxLayer + 1)..targetLayer) {
                    getGraphLayer(l)[id] = longArrayOf()
                }
            }

            var currEntryPoint = entryNodeId

            for (l in maxLayer downTo targetLayer + 1) {
                val best = searchLayerPacked(prepared, currEntryPoint, 1, l)
                if (best.isNotEmpty()) currEntryPoint = best[0].first
            }

            for (l in minOf(targetLayer, maxLayer) downTo 0) {
                val layerMap = getGraphLayer(l)
                val neighbors = searchLayerPacked(prepared, currEntryPoint, searchRadius, l)

                val deg = maxDegreeForLayer(l)
                val picked = neighbors.take(deg).map { it.first }.toLongArray()

                layerMap[id] = picked
                for (nb in picked) addBidirectionalLink(layerMap, id, nb, l, dim)

                neighborCache.clear()
                currEntryPoint = picked.firstOrNull() ?: currEntryPoint
            }

            if (targetLayer > maxLayer) {
                maxLayer = targetLayer
                entryNodeId = id
            }
        }
    }

    override fun clear() = lock.write {
        vectorStoreF.clear()
        vectorStoreQ8.clear()
        vectorStoreQ4.clear()

        val upper = maxOf(maxLayer, LAYER_SCAN_FALLBACK)
        for (l in 0..upper) getGraphLayer(l).clear()

        entryNodeId = -1L
        maxLayer = -1

        vectorCacheF.clear()
        vectorCacheQ8.clear()
        vectorCacheQ4.clear()
        neighborCache.clear()
    }

    override fun shutdown() {}

    override fun findAll(indexValue: Any?) = matchAll(indexValue, 50, 50)
    override fun findAllValues(): Set<Long> = lock.read {
        val out = HashSet<Long>()
        out.addAll(vectorStoreF.keys)
        out.addAll(vectorStoreQ8.keys)
        out.addAll(vectorStoreQ4.keys)
        out
    }

    override fun findAllAbove(v: Any?, i: Boolean) = emptySet<Long>()
    override fun findAllBelow(v: Any?, i: Boolean) = emptySet<Long>()
    override fun findAllBetween(f: Any?, iF: Boolean, t: Any?, iT: Boolean) = emptySet<Long>()

    private companion object {
        private const val LAYER_SCAN_FALLBACK = 64
    }

    private fun removeFromArray(arr: LongArray, target: Long): LongArray? {
        var idx = -1
        for (i in arr.indices) {
            if (arr[i] == target) {
                idx = i; break
            }
        }
        if (idx < 0) return null
        if (arr.size == 1) return longArrayOf()
        val out = LongArray(arr.size - 1)
        if (idx > 0) System.arraycopy(arr, 0, out, 0, idx)
        if (idx < arr.lastIndex) System.arraycopy(arr, idx + 1, out, idx, arr.size - idx - 1)
        return out
    }


    private fun detachFromGraph(id: Long) {
        val upper = maxOf(maxLayer, LAYER_SCAN_FALLBACK)
        for (l in 0..upper) {
            val layerMap = getGraphLayer(l)
            val neighbors = layerMap[id] ?: run {
                layerMap.remove(id)
                continue
            }

            layerMap.remove(id)

            for (nb in neighbors) {
                val nbList = layerMap[nb] ?: continue
                val filtered = removeFromArray(nbList, id) ?: continue
                layerMap[nb] = filtered
            }
        }
        neighborCache.clear()
    }

    private fun pickAnyExistingNodeId(): Long? = when (quantization) {
        VectorQuantization.INT8 ->
            vectorStoreQ8.keys.firstOrNull()
                ?: vectorStoreQ4.keys.firstOrNull()
                ?: vectorStoreF.keys.firstOrNull()

        VectorQuantization.INT4 ->
            vectorStoreQ4.keys.firstOrNull()
                ?: vectorStoreQ8.keys.firstOrNull()
                ?: vectorStoreF.keys.firstOrNull()

        VectorQuantization.NONE ->
            vectorStoreF.keys.firstOrNull()
                ?: vectorStoreQ8.keys.firstOrNull()
                ?: vectorStoreQ4.keys.firstOrNull()
    }
}
