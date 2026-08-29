package database.index

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.IndexPostingMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.interactors.index.impl.DefaultIndexInteractor
import com.onyx.persistence.context.SchemaContext
import entities.index.StringIdentifierEntityIndex
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DefaultIndexInteractorApproximateCandidateTest {

    @Test
    fun `shared candidate budget physically stops a simulated million row posting`() {
        val postings = VirtualMillionPostingMap(
            mapOf(
                "empty" to 0,
                "small" to 3,
                "hot" to 1_000_000
            )
        )
        val interactor = interactor(postings)
        val admitted = arrayListOf<Long>()

        val visits = interactor.visitApproximateCandidates(
            indexValues = listOf("empty", "small", "hot"),
            maxCandidates = 17
        ) {
            admitted += it
            true
        }

        assertEquals(17, visits)
        assertEquals(17, admitted.size)
        assertEquals(17, postings.physicalVisits)
        assertEquals(listOf(6, 9, 14), postings.requestedLimits)
        assertFalse(postings.usedUnboundedTraversal)
    }

    @Test
    fun `hot routes receive a fair share before either can consume the budget`() {
        val postings = VirtualMillionPostingMap(
            mapOf("hot-a" to 1_000_000, "hot-b" to 1_000_000)
        )
        val interactor = interactor(postings)

        val visits = interactor.visitApproximateCandidates(
            listOf("hot-a", "hot-b"),
            maxCandidates = 10
        ) { true }

        assertEquals(10, visits)
        assertEquals(5, postings.visitsByValue["hot-a"])
        assertEquals(5, postings.visitsByValue["hot-b"])
    }

    @Test
    fun `visitor cancellation stops within a hot posting`() {
        val postings = VirtualMillionPostingMap(mapOf("hot" to 1_000_000))
        val interactor = interactor(postings)

        val visits = interactor.visitApproximateCandidates(listOf("hot"), 5000) {
            postings.physicalVisits < 7
        }

        assertEquals(7, visits)
        assertEquals(7, postings.physicalVisits)
        assertFalse(postings.usedUnboundedTraversal)
    }

    private fun interactor(postings: VirtualMillionPostingMap): DefaultIndexInteractor {
        val factory = PostingDiskMapFactory(postings)
        val context = Proxy.newProxyInstance(
            SchemaContext::class.java.classLoader,
            arrayOf(SchemaContext::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "getDataFile" -> factory
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ApproximateCandidateSchemaContext"
                else -> throw UnsupportedOperationException(
                    "Unexpected SchemaContext call: ${method.name}"
                )
            }
        } as SchemaContext
        return DefaultIndexInteractor(
            EntityDescriptor(StringIdentifierEntityIndex::class.java),
            IndexDescriptor(name = "indexValue", type = String::class.java),
            context
        )
    }

    private class VirtualMillionPostingMap(
        private val logicalRowsByValue: Map<Any, Int>
    ) : IndexPostingMap {
        override val valueType: Class<*> = String::class.java
        var physicalVisits: Int = 0
        var usedUnboundedTraversal: Boolean = false
        val requestedLimits: MutableList<Int> = arrayListOf()
        val visitsByValue: MutableMap<Any, Int> = linkedMapOf()

        override fun visitRecordIdsInRange(
            fromValue: Any?,
            fromRecordId: Long,
            includeFrom: Boolean,
            toValue: Any?,
            toRecordId: Long,
            includeTo: Boolean,
            maxVisits: Int,
            visitor: (Long) -> Boolean
        ): Int {
            require(fromValue == toValue) { "Test posting map only supports exact routes" }
            val routeValue = requireNotNull(fromValue)
            requestedLimits += maxVisits
            val logicalRows = logicalRowsByValue[routeValue] ?: 0
            var visits = 0
            while (visits < min(logicalRows, maxVisits)) {
                visits++
                physicalVisits++
                visitsByValue[routeValue] = (visitsByValue[routeValue] ?: 0) + 1
                if (!visitor((fromValue.hashCode().toLong() shl 32) xor visits.toLong())) break
            }
            return visits
        }

        override fun forEachRecordIdInRange(
            fromValue: Any?,
            fromRecordId: Long,
            includeFrom: Boolean,
            toValue: Any?,
            toRecordId: Long,
            includeTo: Boolean,
            action: (Long) -> Unit
        ) {
            usedUnboundedTraversal = true
            throw AssertionError("Approximate candidates must not use exhaustive posting traversal")
        }

        override fun add(indexValue: Any, recordId: Long): Boolean = throw UnsupportedOperationException()
        override fun remove(indexValue: Any, recordId: Long): Boolean = throw UnsupportedOperationException()
        override fun contains(indexValue: Any, recordId: Long): Boolean = false
        override fun forEachDistinctValue(action: (Any) -> Unit) = logicalRowsByValue.keys.forEach(action)
        override fun longSize(): Long = logicalRowsByValue.values.sumOf(Int::toLong)
        override fun clear() = Unit
        override fun clearCache() = Unit
    }

    private class PostingDiskMapFactory(
        private val postings: IndexPostingMap
    ) : DiskMapFactory {
        override fun <T : Map<*, *>> getHashMap(keyType: Class<*>, name: String): T =
            throw UnsupportedOperationException()

        override fun <T : Map<*, *>> getHashMap(keyType: Class<*>, header: Header): T =
            throw UnsupportedOperationException()

        override fun getIndexMap(valueType: Class<*>, name: String): IndexPostingMap = postings
        override fun close(): Boolean = true
        override fun commit() = Unit
        override fun delete() = Unit
        override fun newMapHeader(): Header = Header()
        override fun reset() = Unit
        override fun flush() = Unit
    }
}
