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
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultIndexInteractorSaveTest {

    @Test
    fun orderingEquivalentUnchangedValueDoesNotMutatePostingTree() {
        val fixture = fixture(BigDecimal::class.java)
        fixture.seed(BigDecimal("1.0"))

        fixture.interactor.save(BigDecimal("1.0"), BigDecimal("1.00"), REFERENCE, REFERENCE)

        assertEquals(0, fixture.postings.addCalls)
        assertEquals(0, fixture.postings.removeCalls)
        assertEquals(setOf<Pair<Any, Long>>(BigDecimal("1.0") to REFERENCE), fixture.postings.values)
    }

    @Test
    fun changedValueMovesPostingUsingCallerSuppliedOldValue() {
        val fixture = fixture(String::class.java)
        fixture.seed("old")

        fixture.interactor.save("old", "new", REFERENCE, REFERENCE)

        assertEquals(setOf<Pair<Any, Long>>("new" to REFERENCE), fixture.postings.values)
        assertEquals(1, fixture.postings.removeCalls)
        assertEquals(1, fixture.postings.addCalls)
    }

    @Test
    fun sameReferenceTransitionsBetweenIndexedValueAndNull() {
        val fixture = fixture(String::class.java)
        fixture.seed("indexed")

        fixture.interactor.save("indexed", null, REFERENCE, REFERENCE)

        assertTrue(fixture.postings.values.isEmpty())
        assertEquals(1, fixture.postings.removeCalls)

        fixture.resetCounts()
        fixture.interactor.save(null, "indexed-again", REFERENCE, REFERENCE)

        assertEquals(setOf<Pair<Any, Long>>("indexed-again" to REFERENCE), fixture.postings.values)
        assertEquals(1, fixture.postings.addCalls)
        assertEquals(0, fixture.postings.removeCalls)
    }

    private fun fixture(valueType: Class<*>): Fixture {
        val postings = TrackingPostingMap(valueType)
        val factory = TrackingDiskMapFactory(postings)
        val context = Proxy.newProxyInstance(
            SchemaContext::class.java.classLoader,
            arrayOf(SchemaContext::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "getDataFile" -> factory
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TrackingSchemaContext"
                else -> throw UnsupportedOperationException("Unexpected SchemaContext call: ${method.name}")
            }
        } as SchemaContext
        val descriptor = EntityDescriptor(StringIdentifierEntityIndex::class.java)
        val indexDescriptor = IndexDescriptor(name = "indexValue", type = valueType)
        val interactor = DefaultIndexInteractor(descriptor, indexDescriptor, context)
        return Fixture(context, interactor, postings)
    }

    private data class Fixture(
        @Suppress("unused") val context: SchemaContext,
        val interactor: DefaultIndexInteractor,
        val postings: TrackingPostingMap
    ) {
        fun seed(value: Any) {
            postings.values += value to REFERENCE
            resetCounts()
        }

        fun resetCounts() {
            postings.addCalls = 0
            postings.removeCalls = 0
        }
    }

    private class TrackingPostingMap(override val valueType: Class<*>) : IndexPostingMap {
        val values = linkedSetOf<Pair<Any, Long>>()
        var addCalls = 0
        var removeCalls = 0

        override fun add(indexValue: Any, recordId: Long): Boolean {
            addCalls++
            return values.add(indexValue to recordId)
        }

        override fun remove(indexValue: Any, recordId: Long): Boolean {
            removeCalls++
            return values.remove(indexValue to recordId)
        }

        override fun contains(indexValue: Any, recordId: Long): Boolean =
            values.contains(indexValue to recordId)

        override fun forEachRecordIdInRange(
            fromValue: Any?,
            fromRecordId: Long,
            includeFrom: Boolean,
            toValue: Any?,
            toRecordId: Long,
            includeTo: Boolean,
            action: (Long) -> Unit
        ) = throw UnsupportedOperationException()

        override fun forEachDistinctValue(action: (Any) -> Unit) =
            values.mapTo(linkedSetOf()) { it.first }.forEach(action)

        override fun longSize(): Long = values.size.toLong()

        override fun clear() = values.clear()

        override fun clearCache() = Unit
    }

    private class TrackingDiskMapFactory(private val postings: TrackingPostingMap) : DiskMapFactory {
        override fun <T : Map<*, *>> getHashMap(keyType: Class<*>, name: String): T =
            error("DefaultIndexInteractor must not create a reverse index map")

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

    private companion object {
        const val REFERENCE = 42L
    }
}
