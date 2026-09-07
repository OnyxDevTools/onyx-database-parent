package com.onyx.interactors.query.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.query.impl.collectors.OrderedPageQueryCollector
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator.*
import java.util.Date

/** Page an ordered scalar range without materializing or decoding its discarded matches. */
internal object SecondaryIndexPageQueryPlanner {
    fun collect(query: Query, descriptor: EntityDescriptor, context: SchemaContext): OrderedPageQueryCollector? {
        if (descriptor.hasPartition || query.cache || query.changeListener != null ||
            query.isUpdateOrDelete || query.maxResults <= 0 || query.isDistinct ||
            !query.selections.isNullOrEmpty() || !query.groupBy.isNullOrEmpty() || query.functions().isNotEmpty() ||
            VectorManagedEntity::class.java.isAssignableFrom(descriptor.entityClass)
        ) return null

        val order = query.queryOrders?.singleOrNull() ?: return null
        if (!order.isAscending || order.attribute == descriptor.identifier?.name) return null
        val index = descriptor.indexes[order.attribute]?.takeIf { it.indexType == IndexType.DEFAULT } ?: return null
        val type = index.type.kotlin.javaObjectType
        if (type !in ORDERED_SCALAR_TYPES) return null
        val range = query.criteria?.range(order.attribute, type) ?: return null
        if (!descriptor.hasStoredAttributeOrder(order.attribute)) return null

        val offset = query.firstRow.coerceAtLeast(0).toLong()
        val end = offset + query.maxResults.toLong()
        val page = ArrayList<Reference>()
        var count = 0L
        var previousValue: Any? = null
        var tiedPage = false
        try {
            context.getIndexInteractor(index).visitOrderedRange(
                range.from, range.includeFrom, range.to, range.includeTo
            ) { value, recordId ->
                if (query.isTerminated) return@visitOrderedRange false
                val position = count++
                if (count > context.maxCardinality) return@visitOrderedRange false

                // The ordinary stable sort preserves HashSet arrival order for ties. Only ties
                // touching this page can change its contents or order; retry those via scanners.
                @Suppress("UNCHECKED_CAST")
                if (position > 0 && position >= offset && position <= end &&
                    (previousValue as Comparable<Any>).compareTo(value) == 0
                ) {
                    tiedPage = true
                    return@visitOrderedRange false
                }
                previousValue = value
                if (position >= offset && position < end) page.add(Reference(0L, recordId))
                true
            }
        } catch (_: UnsupportedOperationException) {
            return null
        }
        if (tiedPage) return null

        val collector = OrderedPageQueryCollector(query, context, descriptor)
        if (!query.isTerminated) {
            collector.setTotalCount(count)
            // Release the index lock before loading records or eager relationships.
            page.forEach { reference ->
                collector.collect(reference, if (query.isLazy) null else reference.toManagedEntity(context, descriptor))
            }
        }
        collector.finalizeResults()
        query.resultsCount = collector.getNumberOfResults()
        return collector
    }

    private fun QueryCriteria.range(attribute: String, type: Class<*>): Range? {
        if (this.attribute != attribute || isNot || flip || isOr || isRelationship == true) return null
        if (subCriteria.isEmpty()) return singleRange(type)
        val other = subCriteria.singleOrNull() ?: return null
        if (!other.isAnd || other.subCriteria.isNotEmpty() || other.attribute != attribute ||
            other.isNot || other.flip || other.isOr || other.isRelationship == true
        ) return null

        // Mirror the scanner's existing fusion of a lower and upper bound on the same field.
        val lower: QueryCriteria
        val upper: QueryCriteria
        when {
            operator in LOWER_BOUNDS && other.operator in UPPER_BOUNDS -> { lower = this; upper = other }
            operator in UPPER_BOUNDS && other.operator in LOWER_BOUNDS -> { lower = other; upper = this }
            else -> return null
        }
        if (lower.value?.javaClass != type || upper.value?.javaClass != type) return null
        return Range(lower.value, lower.operator == GREATER_THAN_EQUAL, upper.value, upper.operator == LESS_THAN_EQUAL)
    }

    private fun QueryCriteria.singleRange(type: Class<*>): Range? {
        if (operator == BETWEEN) {
            // Lists have different legacy scanner semantics; only native Pair ranges qualify.
            val endpoints = value as? Pair<*, *> ?: return null
            if (endpoints.first?.javaClass != type || endpoints.second?.javaClass != type) return null
            return Range(endpoints.first, true, endpoints.second, true)
        }
        if (value?.javaClass != type) return null
        return when (operator) {
            GREATER_THAN, GREATER_THAN_EQUAL -> Range(value, operator == GREATER_THAN_EQUAL, null, false)
            LESS_THAN, LESS_THAN_EQUAL -> Range(null, false, value, operator == LESS_THAN_EQUAL)
            else -> null
        }
    }

    private class Range(val from: Any?, val includeFrom: Boolean, val to: Any?, val includeTo: Boolean)

    private val LOWER_BOUNDS = setOf(GREATER_THAN, GREATER_THAN_EQUAL)
    private val UPPER_BOUNDS = setOf(LESS_THAN, LESS_THAN_EQUAL)
    private val ORDERED_SCALAR_TYPES = setOf(
        Byte::class.javaObjectType, Short::class.javaObjectType, Int::class.javaObjectType,
        Long::class.javaObjectType, Float::class.javaObjectType, Double::class.javaObjectType,
        Boolean::class.javaObjectType, Char::class.javaObjectType, String::class.java, Date::class.java
    )
}
