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

    override fun and(builder: IConditionBuilder): IConditionBuilder = apply {
        builder.toCondition()?.let { condition = addCompound(LogicalOperator.AND, it) }
    }

    override fun and(criteria: QueryCriteria): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.AND, SingleCondition(criteria)) }

    override fun or(builder: IConditionBuilder): IConditionBuilder = apply {
        builder.toCondition()?.let { condition = addCompound(LogicalOperator.OR, it) }
    }

    override fun or(criteria: QueryCriteria): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.OR, SingleCondition(criteria)) }

    override fun toCondition(): QueryCondition? = condition

    private fun addCompound(operator: LogicalOperator, next: QueryCondition): QueryCondition {
        val current = condition
        return when {
            current == null -> next
            current is CompoundCondition && current.operator == operator ->
                current.copy(conditions = current.conditions + next)
            else -> CompoundCondition(operator, listOfNotNull(current, next))
        }
    }
}
