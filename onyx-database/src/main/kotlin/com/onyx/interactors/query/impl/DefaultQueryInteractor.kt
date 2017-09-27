package com.onyx.interactors.query.impl

import com.onyx.depricated.CompareUtil
import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.MapBuilder
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.interactors.scanner.impl.FullTableScanner
import com.onyx.interactors.scanner.impl.PartitionFullTableScanner
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyx.interactors.relationship.data.RelationshipTransaction
import com.onyx.extension.*
import com.onyx.persistence.context.Contexts
import com.onyx.interactors.query.QueryInteractor
import com.onyx.interactors.query.data.QuerySortComparator
import com.onyx.interactors.query.data.QueryAttributeResource

import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 3/5/15.
 *
 *
 * Controls how to query a partition
 */
class DefaultQueryInteractor(private var descriptor: EntityDescriptor, private var persistenceManager: PersistenceManager, context: SchemaContext) : QueryInteractor {

    private val contextId = context.contextId
    private var temporaryDataFile: MapBuilder = context.createTemporaryMapBuilder()

    /**
     * Find object ids that match the criteria
     *
     * @param query Query Criteria
     * @return References matching query criteria
     * @throws com.onyx.exception.OnyxException General query exception
     * @since 1.3.0 This has been refactored to remove the logic for meeting critieria.  That has
     * been moved to CompareUtil
     */
    @Throws(OnyxException::class)
    override fun <T : Any?> getReferencesForQuery(query: Query): MutableMap<Reference, T> = getReferencesForCriteria(query, query.criteria!!, null, query.criteria!!.isNot)

    /**
     * Sort using order by query order objects with included values
     *
     * @param query           Query containing order instructions
     * @param referenceValues Query reference values from result of scan
     * @return Sorted references
     * @throws OnyxException Error sorting objects
     */
    @Throws(OnyxException::class)
    override fun <T : Any?> sort(query: Query, referenceValues: MutableMap<Reference, T>): MutableMap<Reference, T> = referenceValues.toSortedMap(QuerySortComparator(query, if (query.queryOrders == null) arrayOf() else query.queryOrders!!.toTypedArray(), descriptor, Contexts.get(contextId)!!))

    /**
     * Hydrate a subset of records with the given identifiers
     *
     * @param query      Query containing all the munging instructions
     * @param references References from query results
     * @return Hydrated entities
     * @throws OnyxException Error hydrating entities
     */
    @Throws(OnyxException::class)
    override fun <T : Any?> referencesToResults(query: Query, references: MutableMap<Reference, T>): List<T> {
        val context = Contexts.get(contextId)!!

        val lower = query.firstRow
        val upper = lower + if(query.maxResults > 0) query.maxResults else references.size

        return references.entries
                .asSequence()
                .filterIndexedTo(ArrayList()) { index, _ -> index in lower..(upper - 1) }
                .map {
                    if(it.value !is IManagedEntity) {
                        @Suppress("UNCHECKED_CAST")
                        it.setValue(it.key.toManagedEntity(context = context, clazz = query.entityType!!) as T)
                        (it.value as IManagedEntity).hydrateRelationships(context, RelationshipTransaction())
                    }
                    it.value
                }
    }

    /**
     * Hydrate given attributes
     *
     * @param query      Query containing selection and count information
     * @param references References found during query execution
     * @return Hydrated key value set for entity attributes
     * @throws OnyxException Cannot hydrate entities
     */
    @Throws(OnyxException::class)
    override fun <T : Any?> referencesToSelectionResults(query: Query, references: Map<Reference, T>): List<T> {
        val context = Contexts.get(contextId)!!
        val scanObjects = QueryAttributeResource.create(query.selections!!.toTypedArray(), descriptor, query, context)

        val lower = query.firstRow
        val upper = lower + if(query.maxResults > 0) query.maxResults else references.size

        val results = references.entries.asSequence()
                .filterIndexedTo(ArrayList()) { index, _ -> index in lower..(upper - 1) }
                .map { entry ->
                    @Suppress("UNCHECKED_CAST")
                    if(entry.value is Map<*,*>)
                        entry.value
                    else {
                        val record = HashMap<String, Any?>()
                        scanObjects.forEach {
                            record.put(it.attribute, when {
                                it.relationshipDescriptor != null && it.relationshipDescriptor.isToOne -> entry.key.toOneRelationshipAsMap(context, it)
                                it.relationshipDescriptor != null && it.relationshipDescriptor.isToMany -> entry.key.toManyRelationshipAsMap(context, it)
                                else -> entry.key.attribute(context, it.attribute, descriptor)
                            })
                        }
                        (entry as MutableMap.MutableEntry<Reference, Any?>).setValue(record)
                        record as T
                    }
                }

        return if(query.isDistinct) results.toHashSet().toList() else results
    }

    /**
     * Delete record with reference ids
     *
     * @param records References to delete
     * @param query   Query object
     * @return Number of entities deleted
     * @throws OnyxException Cannot delete entities
     */
    @Throws(OnyxException::class)
    override fun <T : Any?> deleteRecordsWithReferences(records: Map<Reference, T>, query: Query): Int {

        val context = Contexts.get(contextId)!!
        var deleteCount = 0
        val lower = query.firstRow
        val upper = lower + if(query.maxResults > 0) query.maxResults else records.size

        records.entries.asSequence()
                .filterIndexedTo(ArrayList()) { index, _ -> index in lower..(upper - 1) }
                .forEach {
                    val entity:IManagedEntity? = it.key.toManagedEntity(context, query.entityType!!, descriptor)
                    entity?.deleteAllIndexes(context, it.key.reference)
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
    @Throws(OnyxException::class)
    override fun <T : Any?> updateRecordsWithReferences(query: Query, records: Map<Reference, T>): Int {

        val context = Contexts.get(contextId)!!
        var updateCount = 0
        val lower = query.firstRow
        val upper = lower + if(query.maxResults > 0) query.maxResults else records.size

        records.entries.asSequence()
                .filterIndexedTo(ArrayList()) { index, _ -> index in lower..(upper - 1) }
                .forEach {
                    val entity:IManagedEntity? = it.key.toManagedEntity(context, query.entityType!!, descriptor)
                    val updatedPartitionValue = query.updates.firstOrNull { entity != null && it.fieldName == descriptor.partition?.name && !CompareUtil.compare(entity[context, descriptor, it.fieldName!!], it.value)} != null

                    if(updatedPartitionValue) {
                        entity?.deleteAllIndexes(context, it.key.reference)
                        entity?.deleteRelationships(context)
                        entity?.recordInteractor(context)?.delete(entity)
                    }

                    query.updates.forEach { entity?.set(context = context, descriptor = descriptor, name = it.fieldName!!, value = it.value) }

                    entity?.save(context)
                    entity?.saveIndexes(context, it.key.reference)
                    if(entity != null)
                        updateCount++
                }

        return updateCount
    }

    /**
     * Get the count for a query.  This is used to get the count without actually executing the query.  It is lighter weight
     * than the entire query and in most cases will use the longSize on the disk map data structure if it is
     * for the entire table.
     *
     * @param query Query to identify count for
     * @return The number of records matching query criterium
     * @throws OnyxException Excaption ocurred while executing query
     * @since 1.3.0 Added as enhancement #71
     */
    @Throws(OnyxException::class)
    override fun getCountForQuery(query: Query): Long {
        val context = Contexts.get(contextId)!!
        if (query.isDefaultQuery(descriptor)) {
            val systemEntity = context.getSystemEntityByName(query.entityType!!.name)

            when {
                QueryPartitionMode.ALL == query.partition -> {
                    var resultCount = 0L

                    systemEntity!!.partition!!.entries.forEach {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val dataFile = context.getDataFile(partitionDescriptor)
                        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(partitionDescriptor.entityClass.name, partitionDescriptor.identifier!!.loadFactor.toInt())
                        resultCount += records.longSize()
                    }

                    return resultCount
                }
                else -> {
                    val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
                    val dataFile = context.getDataFile(partitionDescriptor)
                    val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(partitionDescriptor.entityClass.name, partitionDescriptor.identifier!!.loadFactor.toInt())
                    return records.longSize()
                }
            }
        } else {
            val results = this.getReferencesForQuery<Any>(query)
            return results.size.toLong()
        }
    }

    /**
     * Cleanup the query controller references so that we do not have memory leaks.
     * The most important part of this is to recycle the temporary map builders.
     */
    override fun cleanup() {
        val context = Contexts.get(contextId)
        context?.releaseMapBuilder(this.temporaryDataFile)
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
    @Throws(OnyxException::class)
    private fun <T : Any?> getReferencesForCriteria(query: Query, criteria: QueryCriteria<*>, existingReferences: MutableMap<Reference, Reference>?, forceFullScan: Boolean): MutableMap<Reference, T> {
        val context = Contexts.get(contextId)!!
        // Ensure query is still valid
        if (query.isTerminated) {
            return HashMap()
        }

        val scanner = if (forceFullScan) {
            ScannerFactory.getFullTableScanner(context, criteria, query.entityType!!, temporaryDataFile, query, persistenceManager)
        } else {
            ScannerFactory.getScannerForQueryCriteria(context, criteria, query.entityType!!, temporaryDataFile, query, persistenceManager)
        }

        // Scan for records
        // If there are existing references, use those to narrow it down.  Otherwise
        // start from a clean slate
        val criteriaResults: MutableMap<Reference, Reference>

        criteriaResults = if (existingReferences == null) {
            scanner.scan()
        } else {
            if (criteria.isOr || criteria.isNot) {
                scanner.scan()
            } else {
                scanner.scan(existingReferences)
            }
        }

        // If it is a full table scanner.  No need to go any further, we have all we need since
        // The full table scanner compares all criteria
        @Suppress("UNCHECKED_CAST")
        if (scanner is FullTableScanner || scanner is PartitionFullTableScanner)
            return criteriaResults as MutableMap<Reference, T>

        // Go through and ensure all the sub criteria is met
        for (subCriteriaObject in criteria.subCriteria) {
            val subCriteriaResults = getReferencesForCriteria<Reference>(query, subCriteriaObject, criteriaResults, false)
            aggregateFilteredReferences(subCriteriaObject, criteriaResults, subCriteriaResults)
        }

        @Suppress("UNCHECKED_CAST")
        return criteriaResults as MutableMap<Reference, T>
    }

    /**
     * Used to correlate existing reference sets with the criteria met from
     * a single criteria.
     *
     * @param criteria Root Criteria
     * @param totalResults Results from previous scan iterations
     * @param criteriaResults Criteria results used to aggregate a contrived list
     */
    private fun <T : Any?> aggregateFilteredReferences(criteria: QueryCriteria<*>, totalResults: MutableMap<Reference, T>, criteriaResults: MutableMap<Reference, T>) {
        when {
            criteria.isNot ->   totalResults -= criteriaResults.keys
            criteria.isOr ->    totalResults += criteriaResults
            criteria.isAnd ->   totalResults -= totalResults.filterKeys { !criteriaResults.containsKey(it) }.keys
        }
    }

}
