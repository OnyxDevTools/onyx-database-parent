package com.onyx.interactors.record.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.data.PutResult
import com.onyx.extension.common.uuid
import com.onyx.extension.identifier
import com.onyx.extension.set
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

class UUIDRecordInteractor(entityDescriptor: EntityDescriptor, context: SchemaContext) : DefaultRecordInteractor(entityDescriptor, context) {

    override fun save(entity: IManagedEntity): PutResult {
        val identifierValue = entity.identifier(context)

        if ((identifierValue as? String).isNullOrEmpty()) {
            entity[context, entityDescriptor, entityDescriptor.identifier!!.name] = uuid()
        }
        return super.save(entity)
    }
}