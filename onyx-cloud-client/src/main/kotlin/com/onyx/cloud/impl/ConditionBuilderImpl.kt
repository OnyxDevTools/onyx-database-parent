package com.onyx.cloud.impl

import com.onyx.cloud.impl.QueryCondition.CompoundCondition
import com.onyx.cloud.impl.QueryCondition.SingleCondition
import com.onyx.cloud.api.IConditionBuilder
import com.onyx.cloud.api.LogicalOperator
import com.onyx.cloud.api.QueryCriteria

/**
 * Default [IConditionBuilder] implementation for composing [QueryCondition] graphs.
 *
 * The builder maintains an internal [QueryCondition] tree and provides fluent helpers for combining
 * additional criteria with logical `AND`/`OR` operators. It accepts either raw [QueryCriteria] values or
 * other builders, allowing complex conditions to be assembled incrementally.
 *
 * @param criteria optional starting point for the condition tree.
 */
class ConditionBuilderImpl(criteria: QueryCriteria? = null) : IConditionBuilder {
    private var condition: QueryCondition? = criteria?.let { SingleCondition(it) }

    /**
     * Combines the current condition with the one produced by [builder] using a logical `AND`.
     *
     * @param builder condition builder whose result will be merged.
     * @return this instance for fluent chaining.
     */
    override fun and(builder: IConditionBuilder): IConditionBuilder = apply {
        builder.toCondition()?.let { condition = addCompound(LogicalOperator.AND, it) }
    }

    /**
     * Combines the current condition with [criteria] using a logical `AND`.
     *
     * @param criteria additional criteria to merge into the builder.
     * @return this instance for fluent chaining.
     */
    override fun and(criteria: QueryCriteria): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.AND, SingleCondition(criteria)) }

    /**
     * Combines the current condition with the one produced by [builder] using a logical `OR`.
     *
     * @param builder condition builder whose result will be merged.
     * @return this instance for fluent chaining.
     */
    override fun or(builder: IConditionBuilder): IConditionBuilder = apply {
        builder.toCondition()?.let { condition = addCompound(LogicalOperator.OR, it) }
    }

    /**
     * Combines the current condition with [criteria] using a logical `OR`.
     *
     * @param criteria additional criteria to merge into the builder.
     * @return this instance for fluent chaining.
     */
    override fun or(criteria: QueryCriteria): IConditionBuilder =
        apply { condition = addCompound(LogicalOperator.OR, SingleCondition(criteria)) }

    /**
     * Materialises the currently configured condition tree.
     *
     * @return the built [QueryCondition], or `null` when no criteria have been supplied.
     */
    override fun toCondition(): QueryCondition? = condition

    /**
     * Creates or expands a [CompoundCondition] by appending [next] with the supplied [operator].
     *
     * @param operator logical operator used to combine conditions.
     * @param next condition to append to the existing tree.
     * @return the resulting composite condition.
     */
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
