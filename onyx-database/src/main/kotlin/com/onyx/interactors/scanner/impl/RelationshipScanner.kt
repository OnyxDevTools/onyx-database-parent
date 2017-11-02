package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.exception.OnyxException
import com.onyx.exception.InvalidQueryException
import com.onyx.extension.*
import com.onyx.extension.common.copy
import com.onyx.extension.common.instance
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.context.Contexts
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * Scan relationships for matching criteria
 */
class RelationshipScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: DiskMapFactory, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    /**
     * Full Table get all relationships
     *
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableMap<Reference, Reference> {

        val startingPoint = HashMap<Reference, Reference>()
        val context = Contexts.get(contextId)!!

        // We do not support querying relationships by all partitions.  That would be horribly inefficient
        if (this.query.partition === QueryPartitionMode.ALL) throw InvalidQueryException()

        var partitionId = 0L

        if (this.descriptor.hasPartition) {
            val temporaryManagedEntity: IManagedEntity = descriptor.entityClass.instance()
            temporaryManagedEntity[context, descriptor, descriptor.partition!!.name] = query.partition.toString()
            partitionId = temporaryManagedEntity.partitionId(context, descriptor)
        }

        records.references.map { Reference(partitionId, it.recordId) }.forEach { startingPoint.put(it, it) }

        return scan(startingPoint)
    }

    /**
     * Full Scan with existing values
     *
     * @param existingValues Existing values to check criteria
     * @return filtered map of results matching additional criteria
     * @throws OnyxException Cannot scan relationship values
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: MutableMap<Reference, Reference>): MutableMap<Reference, Reference> {
        val context = Contexts.get(contextId)!!

        // Retain the original attribute
        val originalAttribute = criteria.attribute

        // Get the attribute name.  If it has multiple tokens, that means it is another relationship.
        // If that is the case, we gotta find that one
        val segments = originalAttribute!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        // Map <ChildIndex, ParentIndex> // Inverted list so we can use it to scan using an normal full table scanner or index scanner
        val relationshipIndexes = getRelationshipIndexes(segments[0], existingValues)
        val returnValue = HashMap<Reference, Reference>()
        val relationshipDescriptor = this.descriptor.relationships[segments[0]]!!

        // Create a copy of the query so it does not impact the reference of the one controlling the cache
        val queryTuple = copyQuery()
        val copyCriteria = queryTuple.second
        val queryCopy = queryTuple.first

        copyCriteria.attribute = criteria.attribute!!.replaceFirst((segments[0] + "\\.").toRegex(), "")
        copyCriteria.isRelationship = false

        // Get the next scanner because we are not at the end of the line.  Otherwise, we would not have gotten to this place
        val tableScanner = ScannerFactory.getScannerForQueryCriteria(context, copyCriteria, relationshipDescriptor.inverseClass, temporaryDataFile, queryCopy, persistenceManager)

        // Sweet, lets get the scanner.  Note, this very well can be recursive, but sooner or later it will get to the
        // other scanners
        val childIndexes = tableScanner.scan(relationshipIndexes)

        // Swap parent / child after getting results.  This is because we can use the child when hydrating stuff
        childIndexes.keys.forEach { returnValue[relationshipIndexes[it]!!] = it }

        return returnValue
    }

    /**
     * The purpose of this method is to copy a query so that the relationship scanner can treat it as a property
     * scanner without impacting the original query reference.  The original query reference must stay in tact
     * for caching purposes.
     *
     * @since 2.0.0 Fixed a bug related to subsequent and concurrent query matching logic
     *
     * @return Pair query and its applied criteria for scanner.  Both must be cloned
     */
    private fun copyQuery():Pair<Query, QueryCriteria> {
        val queryCopy = Query()
        queryCopy.copy(query)

        return if(queryCopy.criteria != criteria) {
            queryCopy.to(searchForMatchingCriteriaRecursively(queryCopy.criteria!!)!!)
        }
        else {
            queryCopy.criteria = QueryCriteria()
            queryCopy.criteria!!.copy(criteria)
            queryCopy.to(queryCopy.criteria!!)
        }
    }

    /**
     * Recursively search through all criteria to find and clone the criteria that applies to this scanner
     *
     * @since 2.0.0
     */
    private fun searchForMatchingCriteriaRecursively(queryCriteria: QueryCriteria):QueryCriteria? {
        val index = queryCriteria.subCriteria.indexOfFirst { it == criteria }
        return if(index > -1) {
            queryCriteria.subCriteria[index] = QueryCriteria()
            queryCriteria.subCriteria[index].copy(criteria)
            queryCriteria.subCriteria[index]
        } else {
            queryCriteria.subCriteria.firstOrNull { searchForMatchingCriteriaRecursively(it) != null }
        }
    }

    /**
     * Get Relationship Indexes
     *
     * @param attribute Attribute match
     * @param existingValues Existing values to check
     * @return References that match criteria
     */
    @Throws(OnyxException::class)
    private fun getRelationshipIndexes(attribute: String, existingValues: Map<Reference, Reference>): MutableMap<Reference, Reference> {
        if (this.query.partition === QueryPartitionMode.ALL) throw InvalidQueryException()

        val context = Contexts.get(contextId)!!
        val relationshipIndexes = HashMap<Reference, Reference>()

        existingValues.keys.forEach { parentReference ->
            val entity = parentReference.toManagedEntity(context, descriptor)
            val relationshipIdentifiers = entity.relationshipInteractor(context, attribute).getRelationshipIdentifiersWithReferenceId(parentReference)
            val inverseRelationshipDescriptor = entity.inverseRelationshipDescriptor(context, attribute)

            relationshipIdentifiers.forEach { relationshipReference ->
                val referenceId = context.getRecordInteractor(inverseRelationshipDescriptor!!.entityDescriptor).getReferenceId(relationshipReference.identifier!!)
                if(referenceId > 0L) {
                    val childReference = Reference(relationshipReference.partitionId, referenceId)
                    relationshipIndexes.put(childReference, parentReference)
                }
            }
        }

        return relationshipIndexes
    }
}
