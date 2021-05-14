package com.onyx.interactors.query.data

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.extension.common.catchAll
import com.onyx.extension.common.compare
import com.onyx.persistence.IManagedEntity
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
        repeat(orderBy.count()) { parentObjects.add(WeakHashMap()) }
    }

    override fun compare(reference1: Reference, reference2: Reference): Int {
        val context = Contexts.get(contextId)!!

        scanObjects.forEachIndexed { index, scannerProperties ->
            val queryOrder = orderBy[index]
            val attributeValues = parentObjects[index]
            val attribute1 = attributeValues.getOrPut(reference1) { getAttribute(scannerProperties, reference1, context)}
            val attribute2 = attributeValues.getOrPut(reference2) { getAttribute(scannerProperties, reference2, context)}

            var compareValue = 0
            catchAll {
                compareValue = when {
                    attribute2.compare(attribute1, QueryCriteriaOperator.GREATER_THAN) -> if (queryOrder.isAscending) 1 else -1
                    attribute2.compare(attribute1, QueryCriteriaOperator.LESS_THAN) -> if (queryOrder.isAscending) -1 else 1
                    else -> 0
                }
            }

            if(compareValue != 0)
                return@compare compareValue
        }

        return if (reference1 == reference2) 0 else -1
    }

    fun compare(entity1: IManagedEntity, entity2: IManagedEntity): Int {
        val context = Contexts.get(contextId)!!

        scanObjects.forEachIndexed { index, scannerProperties ->
            val queryOrder = orderBy[index]
            val attribute1 = getAttribute(scannerProperties, entity1, context)
            val attribute2 = getAttribute(scannerProperties, entity2, context)

            var compareValue = 0
            catchAll {
                compareValue = when {
                    attribute2.compare(attribute1, QueryCriteriaOperator.GREATER_THAN) -> if (queryOrder.isAscending) 1 else -1
                    attribute2.compare(attribute1, QueryCriteriaOperator.LESS_THAN) -> if (queryOrder.isAscending) -1 else 1
                    else -> 0
                }
            }

            if(compareValue != 0)
                return@compare compareValue
        }

        return if (entity1 === entity2) 0 else -1
    }

    fun compare(entity1: Map<String, Any?>, entity2: Map<String, Any?>): Int {
        orderBy.forEach {
            val attribute1 = entity1[it.attribute]
            val attribute2 = entity2[it.attribute]

            var compareValue = 0
            catchAll {
                compareValue = when {
                    attribute2.compare(attribute1, QueryCriteriaOperator.GREATER_THAN) -> if (it.isAscending) 1 else -1
                    attribute2.compare(attribute1, QueryCriteriaOperator.LESS_THAN) -> if (it.isAscending) -1 else 1
                    else -> 0
                }
            }

            if(compareValue != 0)
                return@compare compareValue
        }

        return if (entity1 === entity2) 0 else -1
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
    private fun getAttribute(queryAttributeResource: QueryAttributeResource, reference: Reference, context: SchemaContext): Any? = getAttribute(queryAttributeResource, reference.toManagedEntity(context, queryAttributeResource.descriptor)!!, context)

    /**
     * Get an attribute value
     *
     * @param queryAttributeResource Scanner property
     * @param entity Entity to get relationship for
     * @return The attribute value.  Can also be a relationship attribute value
     * @throws OnyxException Exception when trying to hydrate attribute
     */
    fun getAttribute(queryAttributeResource: QueryAttributeResource, entity: IManagedEntity, context: SchemaContext): Any? {
        val parts = queryAttributeResource.attributeParts

        return when {
            parts.size == 1 && queryAttributeResource.relationshipDescriptor != null && queryAttributeResource.relationshipDescriptor.isToOne -> entity.getRelationshipFromStore(context, queryAttributeResource.attribute).firstOrNull()
            parts.size == 1 && queryAttributeResource.relationshipDescriptor != null && queryAttributeResource.relationshipDescriptor.isToMany -> entity.getRelationshipFromStore(context, queryAttributeResource.attribute)
            queryAttributeResource.relationshipDescriptor != null && queryAttributeResource.relationshipDescriptor.isToOne -> entity.getRelationshipFromStore(context, queryAttributeResource.attribute).firstOrNull()?.get(context, queryAttributeResource.descriptor, parts.last())
            queryAttributeResource.relationshipDescriptor != null && queryAttributeResource.relationshipDescriptor.isToMany -> entity.getRelationshipFromStore(context, queryAttributeResource.attribute).map { it?.get<Any?>(context, queryAttributeResource.descriptor, parts.last()) }
            else -> entity[context, queryAttributeResource.descriptor, queryAttributeResource.attribute]
        }
    }

}