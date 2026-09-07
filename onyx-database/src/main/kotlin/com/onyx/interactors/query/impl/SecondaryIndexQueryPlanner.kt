package com.onyx.interactors.query.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.interactors.index.IndexInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode

/** Intersect exact secondary indexes without materializing broad posting lists. */
internal object SecondaryIndexQueryPlanner {
    private const val MAX_SEED_RECORDS = 64

    /** Null means the ordinary scanners must execute the query; an empty set is an exact result. */
    fun findReferences(query: Query, descriptor: EntityDescriptor, context: SchemaContext): MutableSet<Reference>? {
        if (VectorManagedEntity::class.java.isAssignableFrom(descriptor.entityClass)) return null
        val root = query.criteria ?: return null
        if (root.subCriteria.isEmpty() || !root.isEqualityConjunction()) return null
        val predicates = query.getAllCriteria()
        if (predicates.any { predicate ->
                val index = descriptor.indexes[predicate.attribute]
                val type = index?.type
                predicate.attribute == descriptor.identifier?.name || predicate.isRelationship == true ||
                    index?.indexType != IndexType.DEFAULT || type == null || type.isArray ||
                    Iterable::class.java.isAssignableFrom(type) || Map::class.java.isAssignableFrom(type) ||
                    Pair::class.java.isAssignableFrom(type) || predicate.value == null || predicate.value is List<*>
            }
        ) return null

        val partitions = when {
            !descriptor.hasPartition -> listOf(0L to descriptor)
            query.partition === QueryPartitionMode.ALL -> context.getAllPartitions(descriptor.entityClass).map {
                it.index to context.getDescriptorForEntity(descriptor.entityClass, it.value)
            }
            else -> {
                val partition = context.getPartitionWithValue(descriptor.entityClass, query.partition)
                    ?: return hashSetOf()
                listOf(partition.index to context.getDescriptorForEntity(descriptor.entityClass, query.partition))
            }
        }

        val matching = HashSet<Reference>()
        try {
            for ((partitionId, partitionDescriptor) in partitions) {
                if (query.isTerminated) return hashSetOf()
                val routes = predicates.map { predicate ->
                    Route(
                        context.getIndexInteractor(partitionDescriptor.indexes.getValue(requireNotNull(predicate.attribute))),
                        requireNotNull(predicate.value)
                    )
                }
                val seed = selectSeed(routes, query)
                if (query.isTerminated) return hashSetOf()
                if (seed == null) return null
                for (recordId in seed.records) {
                    if (query.isTerminated) return hashSetOf()
                    if (routes.all { it === seed.route || it.index.containsExactPosting(it.value, recordId) }) {
                        matching.add(Reference(partitionId, recordId))
                        if (matching.size > context.maxCardinality) {
                            throw MaxCardinalityExceededException(context.maxCardinality)
                        }
                    }
                }
            }
        } catch (_: UnsupportedOperationException) {
            // Nothing has been collected yet, so custom indexes can safely retry through scanners.
            return null
        }
        return matching
    }

    private fun selectSeed(routes: List<Route>, query: Query): Seed? {
        var best: Seed? = null
        for (route in routes) {
            val limit = best?.records?.size ?: MAX_SEED_RECORDS
            val records = ArrayList<Long>(limit + 1)
            route.index.visitExactPostings(listOf(route.value)) { recordId ->
                records.add(recordId)
                records.size <= limit && !query.isTerminated
            }
            // A stopped prefix cannot seed an exact query. Only a fully exhausted route qualifies.
            // Later routes need only beat the smallest complete set already found.
            if (records.size <= limit) {
                best = Seed(route, records)
                if (records.isEmpty()) return best
            }
            if (query.isTerminated) return null
        }
        return best
    }

    private fun QueryCriteria.isEqualityConjunction(): Boolean =
        operator == QueryCriteriaOperator.EQUAL && !isNot && !flip && !isOr &&
            subCriteria.all { it.isAnd && it.isEqualityConjunction() }

    private class Route(val index: IndexInteractor, val value: Any)
    private class Seed(val route: Route, val records: List<Long>)
}
