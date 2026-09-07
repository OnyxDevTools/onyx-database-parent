package com.onyx.persistence.query

import com.onyx.buffer.BufferStream
import com.onyx.extension.common.compare
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreparedMembershipTest {
    @Test
    fun `prepared membership preserves equality coercions nulls and invalid operand errors`() {
        val uuid = UUID(0L, 1L)
        val operands = listOf(
            emptyList<Any?>(), listOf(null), listOf(null, 1, 1, 2),
            listOf(1L, 2L), listOf(1.toShort()), listOf(1.toByte()),
            listOf(0.0, -0.0, Double.NaN), listOf(Float.NaN, -0.0f),
            listOf("1", "null", "two"), listOf(true, false), listOf('1'),
            listOf(BigInteger.ONE), listOf(BigDecimal("1.00")), listOf(uuid),
            listOf(QueryCriteriaOperator.IN, QueryCriteriaOperator.NOT_IN),
            listOf(1L, "2", null, 3.7), listOf(Date(1)), listOf(listOf(1)),
            intArrayOf(1, 2), longArrayOf(1L, 2L), shortArrayOf(1), byteArrayOf(1),
            doubleArrayOf(Double.NaN, -0.0), floatArrayOf(Float.NaN, -0.0f),
            booleanArrayOf(true), charArrayOf('1'), arrayOf("1", null),
            linkedSetOf(1, 2), 1, "invalid",
        )
        val candidates = listOf(
            null, 0, 1, 2, 3, 1L, 1.toShort(), 1.toByte(), 1.9, 3.7,
            0.0, -0.0, Double.NaN, Float.NaN, -0.0f,
            "1", "null", "two", true, false, '1', BigInteger.ONE,
            BigDecimal("1.0"), BigDecimal("1.00"), uuid,
            QueryCriteriaOperator.IN, Date(1), listOf(1), mapOf(Pair("value", 1)),
        )
        for (operator in listOf(QueryCriteriaOperator.IN, QueryCriteriaOperator.NOT_IN)) {
            for (operand in operands) {
                val criterion = QueryCriteria("value", operator, operand)
                val query = Query().apply { criteria = criterion }
                query.withPreparedMemberships {
                    for (candidate in candidates) {
                        assertEquals(
                            outcome { operand.compare(candidate, operator) },
                            outcome { query.compareCriterion(criterion, candidate) },
                            "operand=$operand candidate=$candidate operator=$operator",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `large scalar membership traverses operands once across many candidate rows`() {
        var visits = 0
        val source = Iterable { (0 until 1000).map { visits++; it }.iterator() }
        val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, source)
        val query = Query().apply { criteria = criterion }
        query.withPreparedMemberships {
            assertEquals(1000, (0 until 2000).count { query.compareCriterion(criterion, it) })
            assertEquals(1000, visits)
        }
    }

    @Test
    fun `terminated execution does not evaluate membership operands`() {
        val source = Iterable<Int> { error("Terminated queries must not read operands") }
        val query = Query().apply {
            criteria = QueryCriteria("value", QueryCriteriaOperator.IN, source)
            isTerminated = true
        }
        assertEquals(0, query.withPreparedMemberships { 0 })
    }

    @Test
    fun `reexecution sees in-place list and primitive array mutations`() {
        val list = mutableListOf(1, 2)
        val array = intArrayOf(1, 2)
        for (source in listOf(list, array)) {
            val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, source)
            val query = Query().apply { criteria = criterion }
            query.withPreparedMemberships { assertTrue(query.compareCriterion(criterion, 1)) }
            if (source === list) list[0] = 3 else array[0] = 3
            query.withPreparedMemberships {
                assertFalse(query.compareCriterion(criterion, 1))
                assertTrue(query.compareCriterion(criterion, 3))
            }
            assertNull(query.preparedMemberships)
            if (source === list) list[0] = 4 else array[0] = 4
            assertFalse(query.compareCriterion(criterion, 3))
            assertTrue(query.compareCriterion(criterion, 4))
        }
    }

    @Test
    fun `operand and operator replacement cannot use a stale prepared lookup`() {
        val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, listOf(1, 2))
        val query = Query().apply { criteria = criterion }
        query.withPreparedMemberships {
            criterion.value = listOf(3)
            assertFalse(query.compareCriterion(criterion, 1))
            assertTrue(query.compareCriterion(criterion, 3))
            criterion.operator = QueryCriteriaOperator.NOT_IN
            assertFalse(query.compareCriterion(criterion, 3))
            criterion.operator = QueryCriteriaOperator.EQUAL
            criterion.value = 1
            assertTrue(query.compareCriterion(criterion, 1))
        }
    }

    @Test
    fun `mutable dates and custom objects retain live equality and avoid hashing`() {
        val date = Date(1)
        val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, listOf(date))
        val query = Query().apply { criteria = criterion }
        query.withPreparedMemberships {
            assertTrue(query.compareCriterion(criterion, Date(1)))
            date.time = 2
            assertFalse(query.compareCriterion(criterion, Date(1)))
            assertTrue(query.compareCriterion(criterion, Date(2)))
        }
        val unhashable = Unhashable()
        criterion.value = listOf(unhashable)
        query.withPreparedMemberships { assertTrue(query.compareCriterion(criterion, unhashable)) }
        criterion.value = listOf(null)
        query.withPreparedMemberships { assertFalse(query.compareCriterion(criterion, unhashable)) }
    }

    @Test
    fun `failed and nested executions release or restore the enclosing lookup`() {
        val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, listOf(1))
        val query = Query().apply { criteria = criterion }
        query.withPreparedMemberships {
            val outer = query.preparedMemberships
            assertFailsWith<IllegalStateException> {
                query.withPreparedMemberships { error("scan failed") }
            }
            assertSame(outer, query.preparedMemberships)
            assertTrue(query.compareCriterion(criterion, 1))
        }
        assertNull(query.preparedMemberships)
        assertFailsWith<IllegalStateException> {
            query.withPreparedMemberships { error("scan failed") }
        }
        assertNull(query.preparedMemberships)
    }

    @Test
    fun `prepared state leaves query equality hashing and both wire formats unchanged`() {
        val query = Query().apply { criteria = QueryCriteria("value", QueryCriteriaOperator.IN, listOf(1, 2)) }
        val compact = BufferStream.toBuffer(query).bytes()
        val legacy = BufferStream.toLegacyBuffer(query).bytes()
        val hash = query.hashCode()
        val operand = query.criteria!!.value
        query.withPreparedMemberships {
            assertSame(operand, query.criteria!!.value)
            assertEquals(hash, query.hashCode())
            assertContentEquals(compact, BufferStream.toBuffer(query).bytes())
            assertContentEquals(legacy, BufferStream.toLegacyBuffer(query).bytes())
            for (bytes in listOf(compact, legacy)) {
                val restored = BufferStream.fromBuffer(ByteBuffer.wrap(bytes)) as Query
                assertEquals(query, restored)
                assertNull(restored.preparedMemberships)
                restored.withPreparedMemberships { assertTrue(restored.compareCriterion(restored.criteria!!, 2)) }
            }
        }
    }

    private fun outcome(action: () -> Boolean): Any = try { action() } catch (exception: Exception) { exception.javaClass }
    private fun ByteBuffer.bytes() = ByteArray(remaining()).also { get(it) }
    private class Unhashable {
        override fun hashCode(): Int = error("Custom objects must not be hashed")
    }
}
