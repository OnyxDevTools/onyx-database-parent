package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.recordInteractor
import com.onyx.scan.PartitionReference
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.context.SchemaContext

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
fun PartitionReference.recordInteractor(context: SchemaContext, clazz: Class<*>, descriptor:EntityDescriptor):RecordInteractor {
    if(partition == 0L)
        return descriptor.recordInteractor()

    val systemPartition = context.getPartitionWithId(partition)
    return context.getDescriptorForEntity(clazz, systemPartition!!.value).recordInteractor()
}