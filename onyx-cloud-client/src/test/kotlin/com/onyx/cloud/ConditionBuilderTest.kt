package com.onyx.cloud

import kotlin.test.*

/**
 * Validates logical composition and helper functions provided by [ConditionBuilder].
 */
class ConditionBuilderTest {
    @Test
    fun andCombinesSingleConditionsIntoCompound() {
        val builder = "age".gt(18)
        val result = builder.and("city".eq("NY"))

        assertSame(builder, result)

        val compound = assertIs<QueryCondition.CompoundCondition>(builder.toCondition())
        assertEquals(LogicalOperator.AND, compound.operator)
        assertEquals(2, compound.conditions.size)

        val first = assertIs<QueryCondition.SingleCondition>(compound.conditions[0])
        assertEquals("age", first.criteria.field)
        assertEquals(QueryCriteriaOperator.GREATER_THAN, first.criteria.operator)
        assertEquals(18, first.criteria.value)

        val second = assertIs<QueryCondition.SingleCondition>(compound.conditions[1])
        assertEquals("city", second.criteria.field)
        assertEquals(QueryCriteriaOperator.EQUAL, second.criteria.operator)
        assertEquals("NY", second.criteria.value)
    }

    @Test
    fun andAppendsToExistingCompound() {
        val builder = "age".gt(18)
        builder.and("city".eq("NY"))
        builder.and("status".eq("ACTIVE"))

        val compound = assertIs<QueryCondition.CompoundCondition>(builder.toCondition())
        assertEquals(LogicalOperator.AND, compound.operator)

        val singles = compound.conditions.map { assertIs<QueryCondition.SingleCondition>(it) }
        assertEquals(3, singles.size)
        assertEquals(listOf("age", "city", "status"), singles.map { it.criteria.field })
    }

    @Test
    fun orWrapsExistingCompoundWhenOperatorDiffers() {
        val builder = "age".gt(18)
        builder.and("city".eq("NY"))
        builder.or("status".eq("ACTIVE"))

        val outerCompound = assertIs<QueryCondition.CompoundCondition>(builder.toCondition())
        assertEquals(LogicalOperator.OR, outerCompound.operator)
        assertEquals(2, outerCompound.conditions.size)

        val innerCompound = assertIs<QueryCondition.CompoundCondition>(outerCompound.conditions[0])
        assertEquals(LogicalOperator.AND, innerCompound.operator)
        assertEquals(2, innerCompound.conditions.size)

        val trailing = assertIs<QueryCondition.SingleCondition>(outerCompound.conditions[1])
        assertEquals("status", trailing.criteria.field)
        assertEquals(QueryCriteriaOperator.EQUAL, trailing.criteria.operator)
        assertEquals("ACTIVE", trailing.criteria.value)
    }

    @Test
    fun combiningWithEmptyBuildersSkipsNullConditions() {
        val empty = ConditionBuilder()
        empty.and(ConditionBuilder())
        assertNull(empty.toCondition())

        empty.or("name".eq("Alice"))
        val single = assertIs<QueryCondition.SingleCondition>(empty.toCondition())
        assertEquals("name", single.criteria.field)
        assertEquals(QueryCriteriaOperator.EQUAL, single.criteria.operator)
        assertEquals("Alice", single.criteria.value)
    }

    @Test
    fun helperFunctionsProduceExpectedValues() {
        val between = "age".between(18, 30)
        val betweenCondition = assertIs<QueryCondition.SingleCondition>(between.toCondition())
        assertEquals(QueryCriteriaOperator.BETWEEN, betweenCondition.criteria.operator)
        assertEquals(listOf(18, 30), betweenCondition.criteria.value)

        val inOpBuilder = "tags".inOp(listOf("a", "b", 3))
        val inCondition = assertIs<QueryCondition.SingleCondition>(inOpBuilder.toCondition())
        assertEquals(QueryCriteriaOperator.IN, inCondition.criteria.operator)
        assertEquals("a,b,3", inCondition.criteria.value)
    }
}
