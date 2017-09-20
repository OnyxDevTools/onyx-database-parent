package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Get Descriptor Metadata regarding an entity
 *
 * @param context Schema context entity belongs to
 *
 * @since 2.0.0
 */
fun IManagedEntity.descriptor(context: SchemaContext):EntityDescriptor = context.getDescriptorForEntity(this)

