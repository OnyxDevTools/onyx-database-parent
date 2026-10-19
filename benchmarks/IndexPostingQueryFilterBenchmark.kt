import com.onyx.diskmap.IndexPostingMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.impl.BaselineDiskIndexPostingMap
import com.onyx.diskmap.impl.DiskIndexPostingMap
import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.impl.InMemoryStore
import com.onyx.interactors.record.data.Reference
import java.lang.ref.WeakReference
import java.lang.management.ManagementFactory

object QueryFilterBenchmark {
    private var sink = 0L
    private const val postingCount = 600_000L
    private val query = String("MINUTE".toCharArray())
    private val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @JvmStatic
    fun main(args: Array<String>) {
        val nodes = InMemoryStore(null, "query-benchmark-nodes")
        val data = QueryCountStore(InMemoryStore(null, "query-benchmark-data"))
        try {
            fun map(baseline: Boolean): IndexPostingMap {
                val header = Header().also {
                    it.position = nodes.allocate(Header.HEADER_SIZE)
                    nodes.write(it, it.position)
                }
                return if (baseline) BaselineDiskIndexPostingMap(WeakReference(nodes), WeakReference(data), header, String::class.java)
                    else DiskIndexPostingMap(WeakReference(nodes), WeakReference(data), header, String::class.java)
            }
            val baseline = map(true)
            val improved = map(false)
            val intervals = listOf("MINUTE", "MINUTE_5", "MINUTE_15", "MINUTE_30", "HOUR", "HOUR_4", "DAY", "WEEK", "MONTH")
            (0L until postingCount).forEach { minute ->
                baseline.add("MINUTE", minute * 9 + 1)
                improved.add("MINUTE", minute * 9 + 1)
            }
            val candidates = buildList {
                (postingCount - 390 until postingCount).forEach { minute ->
                    intervals.forEachIndexed { index, interval ->
                        val id = minute * 9 + index + 1
                        add(Reference(7L, id))
                        if (index > 0) { baseline.add(interval, id); improved.add(interval, id) }
                    }
                }
            }
            println("postings=600000 MINUTE stringLength=6 otherPostings=3120 candidates=${candidates.size} expectedMatches=390 compressedOops=false")
            repeat(15) { nativeScan(baseline); nativeScan(improved) }
            measure("baseline native warm", 21, data) { nativeScan(baseline) }
            measure("improved native warm", 21, data) { nativeScan(improved) }
            measure("baseline native cold", 5, data, { baseline.clearCache(); System.gc() }) { nativeScan(baseline) }
            measure("improved native cold", 5, data, { improved.clearCache(); System.gc() }) { nativeScan(improved) }

            repeat(10) { materializeAndIntersect(baseline, candidates); containsAndIntersect(improved, candidates) }
            measure("baseline HashMap then List Reference then HashSet Reference and intersection", 21, data) {
                materializeAndIntersect(baseline, candidates)
            }
            repeat(30) { containsAndIntersect(improved, candidates) }
            measure("improved contains probes and intersection", 21, data) { containsAndIntersect(improved, candidates) }
            println("sink=$sink")
        } finally {
            nodes.close()
            data.close()
        }
    }

    private fun nativeScan(map: IndexPostingMap) {
        var total = 0L
        map.forEachRecordIdInRange(query, Long.MIN_VALUE, true, query, Long.MAX_VALUE, true) { total += it }
        check(total == 9L * postingCount * (postingCount - 1L) / 2L + postingCount)
        sink = total
    }

    private fun materializeAndIntersect(map: IndexPostingMap, candidates: List<Reference>) {
        val matches = HashMap<Long, Any?>()
        map.forEachRecordIdInRange(query, Long.MIN_VALUE, true, query, Long.MAX_VALUE, true) { matches[it] = null }
        val references = matches.keys.map { Reference(7L, it) }
        val matching = HashSet<Reference>()
        references.forEach { matching.add(it) }
        val filtered = candidates.filterTo(HashSet()) { matching.contains(it) }
        check(filtered.size == 390)
        sink = filtered.sumOf { it.reference }
    }

    private fun containsAndIntersect(map: IndexPostingMap, candidates: List<Reference>) {
        val filtered = candidates.filterTo(HashSet()) { map.contains(query, it.reference) }
        check(filtered.size == 390)
        sink = filtered.sumOf { it.reference }
    }

    private fun measure(label: String, repeats: Int, data: QueryCountStore, setup: () -> Unit = {}, action: () -> Unit) {
        val times = ArrayList<Double>()
        val allocations = ArrayList<Long>()
        val reads = ArrayList<Long>()
        repeat(repeats) {
            setup()
            data.reads = 0L
            val before = threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId())
            val start = System.nanoTime()
            action()
            times += (System.nanoTime() - start) / 1_000_000.0
            allocations += threadBean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before
            reads += data.reads
        }
        val median = repeats / 2
        println("$label: medianMs=${times.sorted()[median]} allocatedBytes=${allocations.sorted()[median]} objectReads=${reads.sorted()[median]}")
    }
}

private class QueryCountStore(private val delegate: Store) : Store by delegate {
    var reads = 0L
    override fun <T> getObject(position: Long): T { reads++; return delegate.getObject(position) }
}
