package com.onyx.cloud.api

import com.onyx.cloud.QueryCondition
import com.onyx.cloud.QueryCondition.CompoundCondition
import com.onyx.cloud.QueryCondition.SingleCondition

/**
 * Builder implementation for combining query conditions.
 *
 * @param criteria optional starting criteria.
 */
class ConditionBuilderImpl(criteria: QueryCriteria? = null) : IConditionBuilder {
    private var condition: QueryCondition? = criteria?.let { SingleCondition(it) }

    override fun and(builder: IConditionBuilder): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.AND, builder.toCondition()) }

    override fun and(criteria: QueryCriteria): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.AND, SingleCondition(criteria)) }

    override fun or(builder: IConditionBuilder): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.OR, builder.toCondition()) }

    override fun or(criteria: QueryCriteria): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.OR, SingleCondition(criteria)) }

    override fun toCondition(): QueryCondition =
        condition ?: error("No condition defined")

    private fun addCompound(operator: LogicalOperator, next: QueryCondition): QueryCondition {
        val current = condition
        return when (current) {
            null -> CompoundCondition(operator, listOf(next))
            is CompoundCondition -> CompoundCondition(operator, current.conditions + next)
            is SingleCondition -> CompoundCondition(operator, listOf(current, next))
        }.also { condition = it }
    }
}
