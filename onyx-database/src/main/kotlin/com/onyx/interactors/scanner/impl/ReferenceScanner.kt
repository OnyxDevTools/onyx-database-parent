package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
open class ReferenceScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    /**
     * Not supported for all references
     *
     * @return Map of identifiers.  The key is the partition reference and the value is the reference within file.
     * @throws OnyxException Query exception while trying to scan elements
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> = HashSet()

    /**
     * Retrieve all references except those that are passed in
     *
     * @param existingValues Existing values to scan from
     * @return Remaining values that meet the criteria
     * @throws OnyxException Exception while scanning entity records
     * @since 2.0.0 To support the .not() method
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val matching = HashSet<Reference>()
        (records.references.map { Reference(0L, it.position) } - existingValues).forEach {
            collector?.collect(it, it.toManagedEntity(context, descriptor))
            if(collector == null)
                matching.add(it)
        }
        return matching
    }
}