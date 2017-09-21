package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.depricated.CompareUtil
import com.onyx.fetch.PartitionReference

/**
 * Entity meets the query criteria.  This method is used to determine whether the entity meets all the
 * criteria of the query.  It was implemented so that we no longer have logic in the query controller
 * to sift through scans.  We can now only perform a full table scan once.
 *
 * @param entity Entity to check for criteria
 * @param entityReference The entities reference
 * @param context Schema context used to pull entity descriptors, and such
 * @param descriptor Quick reference to the entities descriptor so we do not have to pull it from the schema context
 * @return Whether the entity meets all the criteria.
 * @throws OnyxException Cannot hydrate or pull an attribute from an entity
 *
 * @since 1.3.0 Simplified query criteria management
 */
@Throws(OnyxException::class)
fun Query.meetsCriteria(entity: IManagedEntity, entityReference: PartitionReference, context: SchemaContext, descriptor: EntityDescriptor): Boolean {

    var subCriteria: Boolean

    // Iterate through
    this.getAllCriteria().forEach {
        if (it.attribute!!.contains(".")) {
            // Compare operator for relationship object
            subCriteria = relationshipMeetsCriteria(entity, entityReference, it, context)
        } else {
            // Compare operator for attribute object
            if (it.attributeDescriptor == null)
                it.attributeDescriptor = descriptor.attributes[it.attribute!!]
            subCriteria = CompareUtil.compare(it.value, entity.get(context = context, name = it.attribute!!), it.operator)
        }
        it.meetsCriteria = subCriteria
    }

    return calculateCriteriaMet(this.criteria!!)
}

/**
 * Calculates the result of the parent criteria and correlates
 * its set of children criteria.  A pre-requisite to invoking this method
 * is that all of the criteria have the meet criteria field set and
 * it does NOT take into account the not modifier in the pre-requisite.
 *
 *
 * @param criteria Root criteria to check.  This maintains the order of operations
 * @return Whether all the criteria are met taking into account the order of operations
 * and the not() modifier
 *
 * @since 1.3.0 Added to enhance insertion based criteria checking
 */
private fun Query.calculateCriteriaMet(criteria: QueryCriteria<*>): Boolean {
    var meetsCriteria = criteria.meetsCriteria

    if (criteria.subCriteria.size > 0) {
        criteria.subCriteria.forEach {
            meetsCriteria = if (it.isOr) { calculateCriteriaMet(it) || meetsCriteria } else { calculateCriteriaMet(it) && meetsCriteria }
        }
    }

    if (criteria.isNot)
        meetsCriteria = !meetsCriteria
    return meetsCriteria
}

/**
 * Relationship meets criteria.  This method will hydrate a relationship for an entity and
 * check its criteria to ensure the criteria is met
 *
 * @param entity Original entity containing the relationship.  This entity may or may not have
 * hydrated relationships.  For that reason we have to go back to the store to
 * retrieve the relationship entities.
 *
 * @param entityReference Used for quick reference so we do not have to retrieve the entities
 * reference before retrieving the relationship.
 *
 * @param criteria Criteria to check for to see if we meet the requirements
 *
 * @param context Schema context used to pull entity descriptors and record controllers and such
 *
 * @return Whether the relationship value has met all of the criteria
 *
 * @throws OnyxException Something bad happened.
 *
 * @since 1.3.0 - Used to remove the dependency on relationship scanners and to allow query caching
 * to do a quick reference to see if newly saved entities meet the criteria
 */
@Throws(OnyxException::class)
private fun Query.relationshipMeetsCriteria(entity: IManagedEntity, entityReference: PartitionReference, criteria: QueryCriteria<*>, context: SchemaContext): Boolean {
    var meetsCriteria = false
    val operator = criteria.operator

    // Grab the relationship from the store
    val relationshipEntities = entity.getRelationshipFromStore(context, criteria.attribute!!, entityReference = entityReference)

    // If there are relationship values, check to see if they meet criteria
    if (relationshipEntities!!.isNotEmpty()) {
        val items = criteria.attribute!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val attribute = items[items.size - 1]

        // All we need is a single match.  If there is a relationship that meets the criteria, move along
        for (relationshipEntity in relationshipEntities) {
            meetsCriteria = CompareUtil.compare(criteria.value, relationshipEntity.get(context = context, name = attribute), operator)
            if (meetsCriteria)
                break
        }
    }
    return meetsCriteria
}