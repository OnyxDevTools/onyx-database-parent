package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.search
import com.onyx.persistence.query.withPreparedMemberships
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class QueryCriteriaConcurrencyTest {
    private val context = DefaultSchemaContext("criteria-concurrency", "criteria-concurrency")

    @Test
    fun `computed predicates overlap and keep nested boolean results isolated per row`() {
        val descriptor = EntityDescriptor(ConcurrentPredicateRow::class.java)
        val grouped = QueryCriteria("first", QueryCriteriaOperator.IN, listOf(true))
            .or(QueryCriteria("second", QueryCriteriaOperator.NOT_IN, listOf(false)))
            .not()
        val query = Query(
            ConcurrentPredicateRow::class.java,
            equal("score", 1).and(grouped).or(equal("overrideValue", true)),
        )
        val together = CyclicBarrier(16)
        val rows = (0 until 16).map { bits ->
            ConcurrentPredicateRow().apply {
                id = bits.toLong()
                value = bits and 1
                first = bits and 2 != 0
                second = bits and 4 != 0
                overrideValue = bits and 8 != 0
                beforeScore = {
                    assertFalse(Thread.holdsLock(query), "Computed getters must run outside the query monitor")
                    together.await(5, TimeUnit.SECONDS)
                }
            }
        }
        val executor = Executors.newFixedThreadPool(rows.size)
        try {
            query.withPreparedMemberships {
                repeat(2) {
                    val results = rows.map { row ->
                        executor.submit<Boolean> {
                            query.meetsCriteria(row, Reference(0, row.id), context, descriptor)
                        }
                    }
                    rows.zip(results).forEach { (row, result) ->
                        assertEquals(
                            (row.value == 1 && !(row.first || row.second)) || row.overrideValue,
                            result.get(10, TimeUnit.SECONDS),
                            "Incorrect nested predicate result for row ${row.id}",
                        )
                        row.value = 1 - row.value
                    }
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `parallel vector predicates publish every score and remove stale scores`() {
        val descriptor = EntityDescriptor(ConcurrentScoredRow::class.java)
        val query = Query(
            ConcurrentScoredRow::class.java,
            equal("score", 1)
                .and(search(VectorSearchQuery(text = "alpha beta", requireAllTerms = false)))
                .and(search(VectorSearchQuery(text = "missing"))),
        )
        val together = CyclicBarrier(8)
        val rows = (1L..64L).map { ordinal ->
            ConcurrentScoredRow().apply {
                id = ordinal
                body = if (id % 2L == 0L) "alpha beta" else "alpha"
                beforeScore = {
                    assertFalse(Thread.holdsLock(query))
                    together.await(5, TimeUnit.SECONDS)
                }
            }
        }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val admittedScores = mapOf(Reference(0, 999) to 0.25f)
            query.fullTextScores = admittedScores
            fun evaluateRows() {
                val results = rows.map { row ->
                    executor.submit<Boolean> {
                        query.meetsCriteria(row, Reference(0, row.id), context, descriptor)
                    }
                }
                results.forEach { result ->
                    // The last clause fails, but the preceding search clause still supplies a score.
                    assertFalse(result.get(10, TimeUnit.SECONDS))
                }
            }

            evaluateRows()
            assertEquals(
                admittedScores + rows.associate { Reference(0, it.id) to if (it.id % 2L == 0L) 1f else 0.5f },
                query.fullTextScores,
            )

            rows.forEach { row ->
                row.body = if (row.id % 2L == 0L) "unrelated" else "alpha beta"
            }
            evaluateRows()
            assertEquals(
                admittedScores + rows.filter { it.id % 2L != 0L }.associate { Reference(0, it.id) to 1f },
                query.fullTextScores,
            )
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private fun equal(attribute: String, value: Any): QueryCriteria =
        QueryCriteria(attribute, QueryCriteriaOperator.EQUAL, value)
}

@Entity
class ConcurrentPredicateRow : ManagedEntity() {
    @Identifier var id: Long = 0
    var value: Int = 0
    var first: Boolean = false
    var second: Boolean = false
    var overrideValue: Boolean = false
    @Transient var beforeScore: (() -> Unit)? = null

    val score: Int
        get() {
            beforeScore?.invoke()
            return value
        }
}

@Entity
class ConcurrentScoredRow : VectorManagedEntity() {
    @Identifier var id: Long = 0
    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var body: String = ""
    @Transient var beforeScore: (() -> Unit)? = null

    val score: Int
        get() {
            beforeScore?.invoke()
            return 1
        }
}
