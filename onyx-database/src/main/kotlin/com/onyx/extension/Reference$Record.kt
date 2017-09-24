package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.recordInteractor
import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.context.SchemaContext
import com.onyx.scan.ScannerProperties

/**
 * Get the record interactor for the partition reference.  This will either use the default record
 * interactor if the partition id is 0 or will lookup the correct descriptor and its corresponding
 * record interactor.
 *
 * @param context Schema context containing all the keys to the kingdom
 * @param clazz Class that the partition reference represents
 * @param descriptor Default entity descriptor for this entity type
 *
 * @since 2.0.0
 */
fun Reference.recordInteractor(context: SchemaContext, clazz: Class<*>, descriptor:EntityDescriptor = context.getBaseDescriptorForEntity(clazz)!!):RecordInteractor {
    if(partition == 0L)
        return descriptor.recordInteractor()

    val systemPartition = context.getPartitionWithId(partition)
    return context.getDescriptorForEntity(clazz, systemPartition!!.value).recordInteractor()
}

/**
 * Hydrate an entity from a map
 *
 * @param attribute Name of attribute to pull from store
 * @param descriptor Entity descriptor that matches this record
 * @return hydrated entity as a map
 * @throws OnyxException Exception why trying to retrieve object
 *
 * @since 2.0.0
 */
@Throws(OnyxException::class)
fun Reference.attribute(context: SchemaContext, attribute:String, descriptor: EntityDescriptor): Any? = recordInteractor(context, descriptor.entityClass, descriptor).getAttributeWithReferenceId(descriptor.attributes[attribute]!!.field, reference)



/**
 * Hydrates a to one relationship and formats in the shape of a map
 *
 * @param properties Scanner properties
 * @return To one relationship as a map
 * @throws OnyxException General exception
 * @since 1.3.0
 */
@Throws(OnyxException::class)
fun Reference.toOneRelationshipAsMap(context: SchemaContext, properties: ScannerProperties): Any? {
    val values = toManyRelationshipAsMap(context, properties)
    return if (values.isNotEmpty()) values[0] else null
}

/**
 * Hydrates a to many relationship and formats in the shape of a map
 *
 * @param properties Scanner properties
 * @return List of to many relationships
 * @throws OnyxException General exception
 * @since 1.3.0
 */
@Throws(OnyxException::class)
fun Reference.toManyRelationshipAsMap(context: SchemaContext, properties: ScannerProperties): List<*> {
    val relationshipInteractor = context.getRelationshipInteractor(properties.relationshipDescriptor)
    val relationshipReferences = relationshipInteractor.getRelationshipIdentifiersWithReferenceId(reference)

    return relationshipReferences.map {
        if (properties.attributeDescriptor != null) {
            it.attribute<Any?>(context, properties.descriptor.entityClass, properties.attributeDescriptor.name, properties.descriptor)
        } else {
            it.recordInteractor(context, properties.descriptor.entityClass, properties.descriptor).getMapWithReferenceId(it.referenceID(context, properties.descriptor.entityClass, properties.descriptor))
        }
    }.toList()
}
