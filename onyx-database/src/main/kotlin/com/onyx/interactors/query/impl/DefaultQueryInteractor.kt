package com.onyx.interactors.query.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyx.extension.*
import com.onyx.extension.common.compare
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.QueryCollectorFactory
import com.onyx.persistence.context.Contexts
import com.onyx.interactors.query.QueryInteractor
import com.onyx.interactors.scanner.impl.*

/**
 * Created by timothy.osborn on 3/5/15.
 *
 *
 * Controls how to query a partition
 */
class DefaultQueryInteractor(private var descriptor: EntityDescriptor, private var persistenceManager: PersistenceManager, context: SchemaContext) : QueryInteractor {

    private val contextId = context.contextId

    /**
     * Find object ids that match the criteria
     *
     * @param query Query Criteria
     * @return References matching query criteria
     * @since 1.3.0 This has been refactored to remove the logic for meeting criteria.  That has
     * been moved to CompareUtil
     */
    override fun <T> getReferencesForQuery(query: Query):QueryCollector<T> {
        val pair = getReferencesForCriteria<T>(query, query.criteria!!, null, query.criteria!!.isNot)
        var collector = pair.second
        if(collector == null) {
            collector = QueryCollectorFactory.create(Contexts.get(contextId)!!, descriptor, query)
            collector.setReferenceSet(pair.first)
        }
        collector.finalizeResults()
        query.resultsCount = collector.getNumberOfResults()

        return collector
    }

    /**
     * Delete record with reference ids
     *
     * @param records References to delete
     * @param query   Query object
     * @return Number of entities deleted
     */
    override fun deleteRecordsWithReferences(records: List<Reference>, query: Query): Int {

        val context = Contexts.get(contextId)!!
        var deleteCount = 0

        records.forEach {
            val entity:IManagedEntity? = it.toManagedEntity(context, query.entityType!!, descriptor)
            entity?.deleteAllIndexes(context, it.reference)
            entity?.deleteRelationships(context)
            entity?.recordInteractor(context)?.delete(entity)
            if(entity != null)
                deleteCount++
        }

        return deleteCount
    }

    /**
     * Update records
     *
     * @param query   Query information containing update values
     * @param records Entity references as a result of the query
     * @return how many entities were updated
     * @throws OnyxException Cannot update entity
     */
    override fun updateRecordsWithReferences(query: Query, records: List<Reference>): Int {

        val context = Contexts.get(contextId)!!
        var updateCount = 0

        records.forEach { it ->
            val entity: IManagedEntity? = it.toManagedEntity(context, query.entityType!!, descriptor)
            val updatedPartitionValue = query.updates.firstOrNull { entity != null && it.fieldName == descriptor.partition?.name && !entity.get<Any?>(context, descriptor, it.fieldName!!).compare(it.value) } != null

            if (updatedPartitionValue) {
                entity?.deleteAllIndexes(context, it.reference)
                entity?.deleteRelationships(context)
                entity?.recordInteractor(context)?.delete(entity)
            }

            query.updates.forEach { entity?.set(context = context, descriptor = descriptor, name = it.fieldName!!, value = it.value) }

            val putResult = entity?.save(context)
            entity?.saveIndexes(context, it.reference, putResult!!.recordId)

            // Update Cached queries
            if (entity != null) {
                context.queryCacheInteractor.updateCachedQueryResultsForEntity(entity, descriptor, entity.reference(putResult!!.recordId, context, descriptor), QueryListenerEvent.UPDATE)
                updateCount++
            }
        }

        return updateCount
    }

    /**
     * Get the count for a query.  This is used to get the count without actually executing the query.  It is lighter weight
     * than the entire query and in most cases will use the longSize on the disk map data structure if it is
     * for the entire table.
     *
     * @param query Query to identify count for
     * @return The number of records matching query criterion
     * @throws OnyxException Exception occurred while executing query
     * @since 1.3.0 Added as enhancement #71
     */
    @Throws(OnyxException::class)
    override fun getCountForQuery(query: Query): Long {
        val context = Contexts.get(contextId)!!
        if (query.isDefaultQuery(descriptor)) {
            val systemEntity = context.getSystemEntityByName(query.entityType!!.name)

            when (QueryPartitionMode.ALL) {
                query.partition -> {
                    var resultCount = 0L

                    val entries = context.getAllPartitions(query.entityType!!)

                    entries.forEach {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val dataFile = context.getDataFile(partitionDescriptor)
                        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, partitionDescriptor.entityClass.name)
                        resultCount += records.longSize()
                    }

                    return resultCount
                }
                else -> {
                    val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
                    val dataFile = context.getDataFile(partitionDescriptor)
                    val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, partitionDescriptor.entityClass.name)
                    return records.longSize()
                }
            }
        } else {
            val results = this.getReferencesForQuery<Nothing>(query)
            return results.getNumberOfResults().toLong()
        }
    }

    /**
     * Get references matching a specific criteria
     *
     * @param query Parent query
     * @param criteria Criteria to get references for
     * @param existingReferences Existing matching references from previous criteria.  Null if this is the first criteria.
     * @param forceFullScan Force a full table scan.
     *
     * @return Filtered references matching criteria
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getReferencesForCriteria(query: Query, criteria: QueryCriteria, existingReferences: MutableSet<Reference>?, forceFullScan: Boolean, collect:Boolean = true):Pair<MutableSet<Reference>, QueryCollector<T>?> {
        val context = Contexts.get(contextId)!!
        // Ensure query is still valid
        if (query.isTerminated) {
            return Pair(HashSet(), null)
        }

        val scanner = if (forceFullScan) {
            ScannerFactory.getFullTableScanner(context, criteria, query.entityType!!, query, persistenceManager)
        } else {
            ScannerFactory.getScannerForQueryCriteria(context, criteria, query.entityType!!, query, persistenceManager)
        }

        if(collect &&
                (scanner is FullTableScanner || (
                    criteria == query.getAllCriteria().last()
                    && !criteria.isNot
                    && !criteria.flip
                    && !criteria.isOr))){
            scanner.isLast = true
        }

        // Check to see if it is a range criteria
        var subCriteriaIsRange = false
        if((criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL || criteria.operator === QueryCriteriaOperator.GREATER_THAN)
                && (criteria.subCriteria.isNotEmpty() && criteria.subCriteria.first().isAnd && !criteria.subCriteria.first().isNot && (criteria.subCriteria.first().operator === QueryCriteriaOperator.LESS_THAN || criteria.subCriteria.first().operator === QueryCriteriaOperator.LESS_THAN_EQUAL))
                && criteria.attribute == criteria.subCriteria.first().attribute
                && scanner is RangeScanner) {
            scanner.isBetween = true
            scanner.rangeFrom = criteria.value
            scanner.rangeTo = criteria.subCriteria.first().value
            scanner.toOperator = criteria.subCriteria.first().operator
            scanner.fromOperator = criteria.operator
            subCriteriaIsRange = true
        } else if((criteria.operator === QueryCriteriaOperator.LESS_THAN || criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL)
                && (criteria.subCriteria.isNotEmpty() && criteria.subCriteria.first().isAnd && !criteria.subCriteria.first().isNot && (criteria.subCriteria.first().operator === QueryCriteriaOperator.GREATER_THAN || criteria.subCriteria.first().operator === QueryCriteriaOperator.GREATER_THAN_EQUAL))
                && criteria.attribute == criteria.subCriteria.first().attribute
                && scanner is RangeScanner) {
            scanner.isBetween = true
            scanner.rangeTo = criteria.value
            scanner.rangeFrom = criteria.subCriteria.first().value
            scanner.fromOperator = criteria.subCriteria.first().operator
            scanner.toOperator = criteria.operator
            subCriteriaIsRange = true
        }


        // Scan for records
        // If there are existing references, use those to narrow it down.  Otherwise
        // start from a clean slate

        val criteriaResults: MutableSet<Reference> = if (existingReferences == null) {
            scanner.scan()
        } else {
            if (criteria.isOr || criteria.isNot) {
                scanner.scan()
            } else {
                scanner.scan(existingReferences)
            }
        }

        if(scanner !is FullTableScanner) {
            // Go through and ensure all the sub criteria is met
            criteria.subCriteria.forEachIndexed { index, subCriteriaObject ->
                if(index == 0 && subCriteriaIsRange)
                    return@forEachIndexed
                val subCriteriaResults = getReferencesForCriteria<T>(query, subCriteriaObject, criteriaResults,
                    forceFullScan = false,
                    collect = false
                )
                aggregateFilteredReferences(subCriteriaObject, criteriaResults, subCriteriaResults.first)
            }
        }

        return Pair(criteriaResults, scanner.collector as QueryCollector<T>?)
    }

    /**
     * Used to correlate existing reference sets with the criteria met from
     * a single criteria.
     *
     * @param criteria Root Criteria
     * @param totalResults Results from previous scan iterations
     * @param criteriaResults Criteria results used to aggregate a contrived list
     */
    private fun aggregateFilteredReferences(criteria: QueryCriteria, totalResults: MutableSet<Reference>, criteriaResults: MutableSet<Reference>) {
        @Suppress("ConvertArgumentToSet") // Nope, not more performant
        when {
            criteria.flip ->  {totalResults.clear(); totalResults += criteriaResults}
            criteria.isOr ->  totalResults += criteriaResults
            criteria.isAnd -> totalResults -= totalResults.filter { !criteriaResults.contains(it) }
        }
    }

}
