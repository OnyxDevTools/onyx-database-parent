package com.onyx.cloud.api

import kotlin.test.*

/**
 * Validates logical composition within [ConditionBuilderImpl].
 */
class ConditionBuilderImplTest {
    @Test
    fun combinesAndConditions() {
        val builder = ConditionBuilderImpl(QueryCriteria("age", QueryCriteriaOperator.GREATER_THAN, 18))
            .and(QueryCriteria("active", QueryCriteriaOperator.EQUAL, true))

        val condition = builder.toCondition() as CompoundCondition
        assertEquals(LogicalOperator.AND, condition.operator)
        assertEquals(2, condition.conditions.size)
    }

    @Test
    fun combinesOrConditionsWithBuilder() {
        val first = ConditionBuilderImpl(QueryCriteria("city", QueryCriteriaOperator.EQUAL, "NY"))
        val second = ConditionBuilderImpl(QueryCriteria("city", QueryCriteriaOperator.EQUAL, "LA"))
        first.or(second)
        val cond = first.toCondition() as CompoundCondition
        assertEquals(LogicalOperator.OR, cond.operator)
        assertEquals(2, cond.conditions.size)
    }

    @Test
    fun orAfterAndAddsCondition() {
        val base = ConditionBuilderImpl(QueryCriteria("age", QueryCriteriaOperator.GREATER_THAN, 18))
            .and(QueryCriteria("active", QueryCriteriaOperator.EQUAL, true))
        val city = ConditionBuilderImpl(QueryCriteria("city", QueryCriteriaOperator.EQUAL, "NY"))
        base.or(city)
        val cond = base.toCondition() as CompoundCondition
        assertEquals(LogicalOperator.OR, cond.operator)
        assertEquals(3, cond.conditions.size)
    }
}

