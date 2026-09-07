package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.data.QuerySortComparator
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class QuerySortComparatorReadTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-null-sort-reads-")
        val location = directory.toString()
        factory = EmbeddedPersistenceManagerFactory(
            location, location, DefaultSchemaContext(location, location), false,
        ).apply {
            storeType = this@QuerySortComparatorReadTest.storeType
            initialize()
        }
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::directory.isInitialized) directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `repeated comparisons load each record once including null sort values`() {
        val values = listOf<Pair<Long?, Long?>>(
            null to null, null to 10L, 10L to null, 10L to 20L,
        )
        for ((firstValue, secondValue) in values) {
            val first = saveRow(1L, firstValue)
            val second = saveRow(2L, secondValue)
            val context = factory.schemaContext
            val descriptor = context.getDescriptorForEntity(QuerySortReadRow::class.java, "")
            val interactor = context.getRecordInteractor(descriptor)
            val firstReference = Reference(0L, interactor.getReferenceId(first.id))
            val secondReference = Reference(0L, interactor.getReferenceId(second.id))

            for (ascending in listOf(true, false)) {
                val order = QueryOrder("rank", ascending)
                val query = Query(QuerySortReadRow::class.java, order)
                val comparator = QuerySortComparator(query, arrayOf(order), descriptor, context)
                val expected = compareValues(firstValue, secondValue) * if (ascending) 1 else -1
                QuerySortReadCounts.rows.set(0)

                repeat(100) {
                    assertEquals(expected, comparator.compare(firstReference, secondReference))
                }

                assertEquals(
                    2, QuerySortReadCounts.rows.get(),
                    "Repeated comparisons must reuse $firstValue and $secondValue (ascending=$ascending)",
                )
            }
        }
    }

    @Test
    fun `lazy sorting preserves null ordering and secondary sort keys`() {
        listOf(null, 2L, 1L, null, 2L, 1L).forEachIndexed { index, rank ->
            saveRow(index + 1L, rank)
        }

        for (ascending in listOf(true, false)) {
            val query = Query(
                QuerySortReadRow::class.java,
                listOf(QueryOrder("rank", ascending), QueryOrder("id", false)),
            )
            val expected = if (ascending) listOf(4L, 1L, 6L, 3L, 5L, 2L)
                else listOf(5L, 2L, 6L, 3L, 4L, 1L)

            val rows = factory.persistenceManager.executeLazyQuery<QuerySortReadRow>(query)
            assertEquals(expected, rows.map { it.id })
            assertEquals(6, query.resultsCount)
        }
    }

    private fun saveRow(id: Long, rank: Long?): QuerySortReadRow =
        factory.persistenceManager.saveEntity(QuerySortReadRow().apply {
            this.id = id
            this.rank = rank
        })

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> = listOf(
            arrayOf(StoreType.FILE), arrayOf(StoreType.MEMORY_MAPPED_FILE),
        )
    }
}

private object QuerySortReadCounts {
    val rows = AtomicInteger()
}

@Entity
class QuerySortReadRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Attribute var rank: Long? = null

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        QuerySortReadCounts.rows.incrementAndGet()
    }
}
