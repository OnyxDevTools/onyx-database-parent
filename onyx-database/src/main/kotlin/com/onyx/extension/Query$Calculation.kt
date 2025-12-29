package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.extension.common.compare
import com.onyx.extension.common.get
import com.onyx.extension.identifier
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.relationship

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
fun Query.meetsCriteria(entity: IManagedEntity?, entityReference: Reference, context: SchemaContext, descriptor: EntityDescriptor): Boolean = synchronized(this) {

    var subCriteria: Boolean

    // Iterate through
    for(it in this.getAllCriteria()) {
        if(it.flip)
            continue
        else if (it.isRelationship!!) {
            subCriteria = if(descriptor.relationships.contains(it.relationship)) {
                relationshipMeetsCriteria(entity, entityReference, it, context)
            } else {
                graphMeetsCriteria(entity, it)
            }
        }
        else {
            // Compare operator for attribute value
            if (it.attributeDescriptor == null)
                it.attributeDescriptor = descriptor.attributes[it.attribute!!]

            val attribute = if (it.attributeDescriptor != null) {
                entity?.get<Any?>(context = context, descriptor = descriptor, name = it.attribute!!)
            } else {
                entity?.get<Any?>(it.attribute!!) // Use Kotlin property accessors
            }

            val comparableAttribute = attribute.normalizeForComparison(it.operator, context)
            subCriteria = it.value.compare(comparableAttribute, it.operator!!)
        }
        it.meetsCriteria = subCriteria
    }

    this.criteria ?: return@synchronized true
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
private fun Query.calculateCriteriaMet(criteria: QueryCriteria): Boolean {
    var meetsCriteria = criteria.meetsCriteria

    if (criteria.subCriteria.size > 0) {
        criteria.subCriteria.forEach {
            if(it.flip)
                return@forEach
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
private fun relationshipMeetsCriteria(entity: IManagedEntity?, entityReference: Reference, criteria: QueryCriteria, context: SchemaContext): Boolean {
    var meetsCriteria = false
    val operator = criteria.operator

    // Grab the relationship from the store
    val relationshipEntities = entity?.getRelationshipFromStore(context, criteria.attribute!!, entityReference = entityReference)

    // If there are relationship values, check to see if they meet criteria
    if (relationshipEntities?.isNotEmpty() == true) {
        val attribute = criteria.attribute!!.split(".").lastOrNull()

        // All we need is a single match.  If there is a relationship that meets the criteria, move along
        for (relationshipEntity in relationshipEntities) {
            val comparisonValue = if (criteria.attribute!!.contains(".")) {
                relationshipEntity?.get(context = context, name = attribute!!)
            } else {
                relationshipEntity?.identifier(context)
            }

            meetsCriteria = criteria.value.compare(comparisonValue.normalizeForComparison(operator, context), operator!!)
            if (meetsCriteria)
                break
        }
    } else {
        meetsCriteria = (operator == QueryCriteriaOperator.IS_NULL)
    }
    return meetsCriteria
}

/**
 * Graph meets criteria.  This method will iterate through a graph to
 * check its criteria to ensure the criteria is met
 *
 * @param entity Original entity containing the graph.  This entity may or may not have
 * criteria property.  For that reason we have to go back to the store to
 * retrieve the relationship entities.
 *
 * @param criteria Criteria to check for to see if we meet the requirements
 *
 * @return Whether the graph value has met the criteria
 *
 * @throws OnyxException Something bad happened.
 *
 */
@Throws(OnyxException::class)
private fun graphMeetsCriteria(entity: IManagedEntity?, criteria: QueryCriteria): Boolean {
    val value = entity.get<Any?>(criteria.attribute!!)
    if (value is List<*>) {
        return value.any {
            criteria.value.compare(it.normalizeForComparison(criteria.operator, null), criteria.operator!!)
        }
    }
    return criteria.value.compare(value.normalizeForComparison(criteria.operator, null), criteria.operator!!)
}

private fun Any?.normalizeForComparison(operator: QueryCriteriaOperator?, context: SchemaContext?): Any? {
    if (operator != QueryCriteriaOperator.IN && operator != QueryCriteriaOperator.NOT_IN) return this

    return when (this) {
        is IManagedEntity -> runCatching { this.identifier(context) }.getOrNull() ?: this
        is Map<*, *> -> if (this.size == 1) this.values.firstOrNull() else this
        is Iterable<*> -> this.map { it.normalizeForComparison(operator, context) }
        is Array<*> -> this.map { it.normalizeForComparison(operator, context) }
        else -> this
    }
}
