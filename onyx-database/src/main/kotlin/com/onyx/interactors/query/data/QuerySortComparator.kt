package com.onyx.interactors.query.data

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.depricated.CompareUtil
import com.onyx.extension.attribute
import com.onyx.extension.common.catchAll
import com.onyx.extension.toManyRelationshipAsMap
import com.onyx.extension.toOneRelationshipAsMap
import com.onyx.persistence.context.Contexts

import java.util.*

/**
 * Created by timothy.osborn on 2/11/15.
 * This class sorts query results only hydrating what it needs without putting attributes and entities in memory.
 */
class QuerySortComparator(query: Query, private val orderBy: Array<QueryOrder>, descriptor: EntityDescriptor, context: SchemaContext) : Comparator<Reference> {
    private var contextId: String = context.contextId
    private var scanObjects = QueryAttributeResource.create(orderBy.map { it.attribute }.toTypedArray(), descriptor, query, context)
    private val parentObjects = ArrayList<MutableMap<Reference, Any?>>()

    init {
        orderBy.forEach { parentObjects.add(WeakHashMap()) }
    }

    override fun compare(reference1: Reference, reference2: Reference): Int {
        val context = Contexts.get(contextId)!!

        scanObjects.forEachIndexed { index, scannerProperties ->
            val queryOrder = orderBy[index]
            val attributeValues = parentObjects[index]
            val attribute1 = attributeValues.getOrPut(reference1) { getAttributeToCompare(scannerProperties, reference1, context)}
            val attribute2 = attributeValues.getOrPut(reference2) { getAttributeToCompare(scannerProperties, reference2, context)}

            var compareValue = 0
            catchAll {
                compareValue = when {
                    CompareUtil.compare(attribute2, attribute1, QueryCriteriaOperator.GREATER_THAN) -> if (queryOrder.isAscending) 1 else -1
                    CompareUtil.compare(attribute2, attribute1, QueryCriteriaOperator.LESS_THAN) -> if (queryOrder.isAscending) -1 else 1
                    else -> 0
                }
            }

            if(compareValue != 0)
                return@compare compareValue
        }

        return if (reference1 == reference2) 0 else -1
    }

    /**
     * Get an attribute value
     *
     * @param queryAttributeResource Scanner property
     * @param reference Object reference
     * @return The attribute value.  Can also be a relationship attribute value
     * @throws OnyxException Exception when trying to hydrate attribute
     */
    @Throws(OnyxException::class)
    private fun getAttributeToCompare(queryAttributeResource: QueryAttributeResource, reference: Reference, context: SchemaContext): Any? = when {
        queryAttributeResource.relationshipDescriptor != null && queryAttributeResource.relationshipDescriptor.isToOne -> reference.toOneRelationshipAsMap(context, queryAttributeResource)
        queryAttributeResource.relationshipDescriptor != null && queryAttributeResource.relationshipDescriptor.isToMany -> reference.toManyRelationshipAsMap(context, queryAttributeResource)
        else -> reference.attribute(context, queryAttributeResource.attribute, queryAttributeResource.descriptor)
    }
}