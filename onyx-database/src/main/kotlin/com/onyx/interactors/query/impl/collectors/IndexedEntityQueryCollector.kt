package com.onyx.interactors.query.impl.collectors

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteriaOperator

/** Keeps the complete indexed match domain, loading entities only for the requested page. */
internal class IndexedEntityQueryCollector(
    query: Query,
    context: SchemaContext,
    descriptor: EntityDescriptor,
) : BaseQueryCollector<IManagedEntity>(query, context, descriptor) {

    /** The index has already established membership; no entity is needed to count this match. */
    fun collectReference(reference: Reference) {
        // Partition scanners share a collector. Admission and cardinality must be atomic
        // across partitions, and references must retain their arrival order for cached pages.
        referenceLock.perform {
            if (references.size >= context.maxCardinality) {
                throw MaxCardinalityExceededException(context.maxCardinality)
            }
            references.add(reference)
            increment()
        }
    }

    override fun collect(reference: Reference, entity: IManagedEntity?) {
        // A forced table scan still establishes membership by evaluating the loaded entity.
        if (entity != null) collectReference(reference)
    }

    override fun setReferenceSet(value: MutableSet<Reference>) {
        value.forEach(::collectReference)
    }

    override fun finalizeResults() {
        if (isFinalized) return
        if (!query.isLazy) {
            results = getLimitedReferences().mapNotNullTo(ArrayList()) {
                it.toManagedEntity(context, descriptor)
            }
        }
        // The page is already bounded. The base collector hydrates its eager relationships;
        // lazy results retain references until the caller accesses an item.
        super.finalizeResults()
    }

    companion object {
        fun supports(query: Query, descriptor: EntityDescriptor): Boolean {
            if ((!query.isLazy && query.maxResults <= 0) || query.isUpdateOrDelete ||
                !query.queryOrders.isNullOrEmpty() || !query.selections.isNullOrEmpty() ||
                !query.groupBy.isNullOrEmpty() || query.functions().isNotEmpty() ||
                VectorManagedEntity::class.java.isAssignableFrom(descriptor.entityClass)
            ) return false

            val criteria = query.criteria ?: return false
            return criteria.operator == QueryCriteriaOperator.EQUAL &&
                criteria.subCriteria.isEmpty() && !criteria.isNot && !criteria.flip && !criteria.isOr &&
                criteria.value != null && criteria.value !is List<*> &&
                criteria.attribute != descriptor.identifier?.name &&
                descriptor.indexes[criteria.attribute]?.indexType == IndexType.DEFAULT
        }
    }
}
