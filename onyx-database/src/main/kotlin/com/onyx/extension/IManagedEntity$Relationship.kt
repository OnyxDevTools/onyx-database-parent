package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.OnyxException
import com.onyx.fetch.PartitionReference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.relationship.data.RelationshipTransaction
import com.onyx.interactors.relationship.RelationshipInteractor
import com.onyx.interactors.relationship.data.RelationshipReference
import java.util.ArrayList

val RELATIONSHIP_MAP_LOAD_FACTOR = 2

/**
 * Get relationship controller
 *
 * @param context Schema Context entity belongs to
 * @param name Name of property that corresponds to the relationship
 * @return Corresponding relationship controller for an entity and specified property name
 */
fun IManagedEntity.relationshipInteractor(context: SchemaContext, name:String, descriptor: EntityDescriptor = descriptor(context)): RelationshipInteractor = context.getRelationshipInteractor(descriptor.relationships[name]!!)

/**
 * Save all relationships within an entity
 *
 * @param context Schema Context entity belongs to
 * @param transaction Determines and prevents recursive relationship persistence
 *
 * @since 2.0.0
 */
@JvmOverloads
fun IManagedEntity.saveRelationships(context: SchemaContext, transaction: RelationshipTransaction = RelationshipTransaction(), descriptor: EntityDescriptor = descriptor(context)) {
    if (descriptor.hasRelationships) {
        if(!transaction.contains(this, context)) {
            transaction.add(this, context)
            descriptor.relationships.values.forEach { relationshipInteractor(context, it.name, descriptor).saveRelationshipForEntity(this, transaction) }
        }
    }
}

/**
 * Delete relationship references for an entity
 *
 * @param context Schema Context entity belongs to
 * @param transaction Entity relationship transaction prevents recursive deletes.  Not required
 * @since 2.0.0
 */
@JvmOverloads
fun IManagedEntity.deleteRelationships(context: SchemaContext, transaction: RelationshipTransaction = RelationshipTransaction(), descriptor: EntityDescriptor = descriptor(context)) = descriptor.relationships.values.forEach { relationshipInteractor(context, it.name, descriptor).deleteRelationshipForEntity(this, transaction) }

/**
 * Retrieve a relationship descriptor for an entity given the property name
 * @param context Schema Context entity belongs to
 * @param name Name of relationship
 * @param descriptor Entity descriptor this entity
 *
 * @since 2.0.0
 */
fun IManagedEntity.relationshipDescriptor(context: SchemaContext, name:String?, descriptor: EntityDescriptor = descriptor(context)):RelationshipDescriptor? = descriptor.relationships[name]

/**
 * Get an inverse relationship descriptor based on the parent's relationship name
 *
 * @param context Schema Context entity belongs to
 * @param name Name of relationship
 * @param descriptor Entity descriptor this entity
 *
 * @since 2.0.0
 */
fun IManagedEntity.inverseRelationshipDescriptor(context: SchemaContext, name:String?, descriptor: EntityDescriptor = descriptor(context)):RelationshipDescriptor? {
    val relationshipDescriptor = descriptor.relationships[name] ?: return null
    return context.getDescriptorForEntity(relationshipDescriptor.inverseClass, "").relationships[relationshipDescriptor.inverse]
}

/**
 * Get the relationship references within the store
 * @param context Schema Context entity belongs to
 * @param relationship Name of relationship
 * @since 2.0.0
 */
fun IManagedEntity.relationshipReferenceMap(context: SchemaContext, relationship: String):MutableMap<RelationshipReference, MutableSet<RelationshipReference>>? = getDataFile(context).getHashMap(this::class.java.name + relationship, RELATIONSHIP_MAP_LOAD_FACTOR) as MutableMap<RelationshipReference, MutableSet<RelationshipReference>>?

/**
 * Hydrate all relationships for this entity
 *
 * @param context Schema Context entity belongs to
 * @param transaction Contains relationship transaction information in order to prevent recursive hydration
 * @since 2.0.0
 */
fun IManagedEntity.hydrateRelationships(context: SchemaContext, transaction: RelationshipTransaction = RelationshipTransaction(), descriptor: EntityDescriptor = context.getDescriptorForEntity(this)) {
    if(transaction.contains(this, context))
        return
    transaction.add(this, context)
    descriptor.relationships.values.forEach { relationshipInteractor(context, it.name, descriptor).hydrateRelationshipForEntity(this, transaction, false) }
}

/**
 * Gets hydrated relationships from the store.  Also note, this will pass 1 to 1 as a list.  It will require further
 * work to aggregate from list if the intent is to hydrate.
 *
 * @param context Schema Context entity belongs to
 * @param relationship Name of relationship
 * @param entityReference Entity reference within entire context.  This should take into account whether it is in a partition.
 *                        If none is passed in, it will look it up.
 *
 * @since 2.0.0
 */
@Throws(OnyxException::class)
fun IManagedEntity.getRelationshipFromStore(context: SchemaContext, relationship: String, entityReference:PartitionReference? = reference(context)): List<IManagedEntity>? {
    val slices = relationship.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var descriptor: EntityDescriptor? = descriptor(context)
    var relationshipDescriptor: RelationshipDescriptor? = null

    // Iterate through and grab the right descriptor
    try {
        slices
            .find { descriptor!!.relationships[it] != null }
            .let {
                relationshipDescriptor = descriptor!!.relationships[it]
                descriptor = context.getBaseDescriptorForEntity(relationshipDescriptor!!.inverseClass)
            }
    } catch (e: NullPointerException) {
        return null
    }

    val relationshipInteractor = context.getRelationshipInteractor(relationshipDescriptor!!)
    val relationshipReferences = relationshipInteractor.getRelationshipIdentifiersWithReferenceId(entityReference!!)
    val entities = ArrayList<IManagedEntity>()

    relationshipReferences.forEach {
        var relationshipEntity = it.toManagedEntity(context, relationshipDescriptor!!.inverseClass)
        relationshipEntity = relationshipEntity!!.recordInteractor(context).getWithId(it.identifier!!)
        if (relationshipEntity != null)
            entities.add(relationshipEntity)
    }

    return entities
}
