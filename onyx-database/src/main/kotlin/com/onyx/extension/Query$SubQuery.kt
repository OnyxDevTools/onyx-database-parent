package com.onyx.extension

import com.onyx.extension.common.get
import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryBuilder
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator

/**
 * Resolves sub-queries used within `IN` and `NOT_IN` criteria by executing the
 * nested query and replacing the value with the resulting identifier list.
 */
fun Query.resolveSubQueries(persistenceManager: PersistenceManager) {
    criteria?.let { resolveCriteria(it, persistenceManager) }
}

private fun resolveCriteria(criteria: QueryCriteria, persistenceManager: PersistenceManager) {
    if (criteria.operator == QueryCriteriaOperator.IN || criteria.operator == QueryCriteriaOperator.NOT_IN) {
        criteria.value = resolveInOperatorValue(criteria.value, persistenceManager)
    }

    criteria.subCriteria.forEach { resolveCriteria(it, persistenceManager) }
}

private fun resolveInOperatorValue(value: Any?, persistenceManager: PersistenceManager): Any? {
    val subQueryBuilder = when (value) {
        is QueryBuilder -> value
        is Query -> QueryBuilder(persistenceManager, value)
        else -> null
    }

    if (subQueryBuilder == null) {
        return when (value) {
            is Iterable<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> value
        }
    }

    val records = subQueryBuilder.list<Any?>()
    if (records.isEmpty()) return emptyList<Any?>()

    val descriptor = subQueryBuilder.query.entityType?.let {
        runCatching { persistenceManager.context.getDescriptorForEntity(it, subQueryBuilder.query.partition) }.getOrNull()
    }

    val ids = records.flatMap { extractIdentifierValues(it, descriptor, persistenceManager) }.filterNotNull()
    return if (ids.isNotEmpty()) ids.distinct() else records
}

private fun extractIdentifierValues(
    record: Any?,
    descriptor: EntityDescriptor?,
    persistenceManager: PersistenceManager,
): List<Any?> = when (record) {
    null -> emptyList()
    is String, is Number, is Boolean -> listOf(record)
    is IManagedEntity -> descriptor?.let { listOf(record.identifier(persistenceManager.context, it)) } ?: listOf(record)
    is Map<*, *> -> {
        if (record.size == 1) {
            listOf(record.values.first())
        } else {
            descriptor?.identifier?.name?.let { listOf(record[it]) } ?: listOf(record)
        }
    }
    else -> descriptor?.identifier?.field?.name?.let { runCatching { record.get<Any?>(it) }.getOrNull() }?.let { listOf(it) }
        ?: listOf(record)
}
