package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.*
import com.onyx.extension.common.castTo
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode

/**
 * Validates a query to ensure it is valid before executing it
 *
 * @param context Context to run query on
 * @param descriptor Entity descriptor for the entity it is querying
 * @since 2.0.0
 */
@Throws(OnyxException::class)
fun Query.validate(context: SchemaContext, descriptor: EntityDescriptor = context.getDescriptorForEntity(this.entityType, "")):Boolean {
    // If there are no criteria, add a dummy criteria to the list
    if (this.criteria == null) {
        this.criteria = QueryCriteria<Any>(descriptor.identifier!!.name, QueryCriteriaOperator.NOT_EQUAL)
    }

    definePartition(context)

    this.getAllCriteria()
    this.sortCriteria(descriptor) // Optimize Criteria

    this.updates.forEach {
        val attribute = descriptor.attributes[it.fieldName]
        val indexDescriptor = descriptor.indexes[it.fieldName]

        it.attributeDescriptor = attribute

        if (indexDescriptor != null) {
            it.indexController = context.getIndexController(indexDescriptor)
        }

        // Attribute is defined
        if (attribute == null) throw AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE)

        // Value is null and the field is not nullable
        if (!attribute.isNullable && it.value == null) throw AttributeNonNullException(AttributeNonNullException.ATTRIBUTE_NULL_EXCEPTION, attribute.name)

        // String length is not within entity specs
        if (it.value is String && (it.value as String).length > attribute.size && attribute.size > -1) throw AttributeSizeException(AttributeSizeException.ATTRIBUTE_SIZE_EXCEPTION, attribute.name)

        // Cannot update an identifier
        if (descriptor.identifier!!.name.equals(it.fieldName!!, ignoreCase = true)) throw AttributeUpdateException(AttributeUpdateException.ATTRIBUTE_UPDATE_IDENTIFIER, it.fieldName)

        // Check casting ability for type mismatch
        if (it.value != null) {
            if (it.value!!.javaClass != attribute.type) {
                if (attribute.type.isPrimitive && attribute.type == it.value!!::class.javaPrimitiveType) {}
                else {
                    try {
                        it.value = it.value.castTo(attribute.type)
                    } catch (e: Exception) {
                        throw AttributeTypeMismatchException(AttributeTypeMismatchException.ATTRIBUTE_TYPE_MISMATCH, attribute.type, it.value?.javaClass, attribute.name)
                    }
                }
            }
        }
    }

    return true
}

/**
 * Set the partition field on a query based on the query criteria
 *
 * @param context Schema context
 * @since 2.0.0
 */
@Throws(OnyxException::class)
fun Query.definePartition(context: SchemaContext) {
    if (hasPartitionField(context) && this.partition == "") {
        val baseDescriptor = context.getBaseDescriptorForEntity(this.entityType!!)
        if (baseDescriptor!!.partition != null || this.partition == "") {
            val partitionCriteria = getAllCriteria().find { (it.operator == QueryCriteriaOperator.EQUAL && it.attribute.equals(baseDescriptor.partition!!.name) && !it.isNot) }
            partition = partitionCriteria?.value?.toString() ?: QueryPartitionMode.ALL
        }
    }
}

/**
 * Helper for detecting whether an entity is partition-able
 *
 * @param context Schema context
 * @return whether that entity type is partitioned
 * @since 2.0.0
 */
@Throws(OnyxException::class)
fun Query.hasPartitionField(context: SchemaContext): Boolean = context.getBaseDescriptorForEntity(entityType!!)?.partition != null
