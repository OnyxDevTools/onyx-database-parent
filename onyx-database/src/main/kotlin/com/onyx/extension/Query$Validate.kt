package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.*
import com.onyx.extension.common.castTo
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.resolveApproximateIndexCandidateQuery
import com.onyx.persistence.query.resolveVectorSearchQuery
import com.onyx.persistence.query.resolveHnswSearchQuery
import com.onyx.persistence.VectorManagedEntity

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
        this.criteria = QueryCriteria(descriptor.identifier!!.name, QueryCriteriaOperator.NOT_EQUAL)
    }

    definePartition(context)

    val allCriteria = this.getAllCriteria()
    val approximateCandidates = allCriteria.filter {
        it.operator == QueryCriteriaOperator.CANDIDATES
    }
    if (approximateCandidates.isNotEmpty()) {
        require(approximateCandidates.size == 1 && criteria === approximateCandidates.single() &&
            approximateCandidates.single().subCriteria.isEmpty()) {
            "CANDIDATES must be the sole root criterion; filter its admitted rows in the caller"
        }
        require(!isUpdateOrDelete) {
            "CANDIDATES is a read-only approximate admission operation"
        }
        require(!cache && changeListener == null) {
            "CANDIDATES does not support query caching or live listeners"
        }
        require(!approximateCandidates.single().isNot && !approximateCandidates.single().flip) {
            "Approximate CANDIDATES criteria cannot be negated"
        }
        resolveApproximateIndexCandidateQuery(approximateCandidates.single().value)
        if (descriptor.hasPartition) {
            require(partition != QueryPartitionMode.ALL && partition.toString().isNotBlank()) {
                "CANDIDATES requires one concrete partition for partitioned entities"
            }
        }
    }

    val approximateSearches = allCriteria.filter {
        it.operator == QueryCriteriaOperator.SEARCH_CANDIDATES
    }
    if (approximateSearches.isNotEmpty()) {
        require(approximateSearches.size == 1 && criteria === approximateSearches.single() &&
            approximateSearches.single().subCriteria.isEmpty()) {
            "SEARCH_CANDIDATES must be the sole root criterion; filter its admitted rows in the caller"
        }
        require(!isUpdateOrDelete) {
            "SEARCH_CANDIDATES is a read-only approximate admission operation"
        }
        require(!cache && changeListener == null) {
            "SEARCH_CANDIDATES does not support query caching or live listeners"
        }
        require(!approximateSearches.single().isNot && !approximateSearches.single().flip) {
            "Approximate SEARCH_CANDIDATES criteria cannot be negated"
        }
        require(approximateSearches.single().attribute == Query.FULL_TEXT_ATTRIBUTE) {
            "SEARCH_CANDIDATES requires the ${Query.FULL_TEXT_ATTRIBUTE} field"
        }
        require(VectorManagedEntity::class.java.isAssignableFrom(requireNotNull(entityType))) {
            "SEARCH_CANDIDATES requires a VectorManagedEntity"
        }
        val searchQuery = requireNotNull(
            resolveVectorSearchQuery(approximateSearches.single().value)
        ) { "SEARCH_CANDIDATES requires a lexical VectorSearchQuery" }
        require(!searchQuery.text.isNullOrBlank() && searchQuery.semantic == null) {
            "SEARCH_CANDIDATES supports text-only VectorSearchQuery values"
        }
        if (descriptor.hasPartition) {
            require(partition != QueryPartitionMode.ALL && partition.toString().isNotBlank()) {
                "SEARCH_CANDIDATES requires one concrete partition for partitioned entities"
            }
        }
    }

    val hnswSearches = allCriteria.filter {
        it.operator == QueryCriteriaOperator.HNSW_CANDIDATES
    }
    if (hnswSearches.isNotEmpty()) {
        require(hnswSearches.size == 1 && criteria === hnswSearches.single() &&
            hnswSearches.single().subCriteria.isEmpty()) {
            "HNSW_CANDIDATES must be the sole root criterion; filter its admitted rows in the caller"
        }
        require(!isUpdateOrDelete) {
            "HNSW_CANDIDATES is a read-only approximate admission operation"
        }
        require(!cache && changeListener == null) {
            "HNSW_CANDIDATES does not support query caching or live listeners"
        }
        require(!hnswSearches.single().isNot && !hnswSearches.single().flip) {
            "Approximate HNSW_CANDIDATES criteria cannot be negated"
        }
        require(hnswSearches.single().attribute == Query.FULL_TEXT_ATTRIBUTE) {
            "HNSW_CANDIDATES requires the ${Query.FULL_TEXT_ATTRIBUTE} field"
        }
        require(VectorManagedEntity::class.java.isAssignableFrom(requireNotNull(entityType))) {
            "HNSW_CANDIDATES requires a VectorManagedEntity"
        }
        resolveHnswSearchQuery(hnswSearches.single().value)
        if (descriptor.hasPartition) {
            require(partition != QueryPartitionMode.ALL && partition.toString().isNotBlank()) {
                "HNSW_CANDIDATES requires one concrete partition for partitioned entities"
            }
        }
    }

    this.updates.forEach {
        val attribute = descriptor.attributes[it.fieldName]
        val indexDescriptor = descriptor.indexes[it.fieldName]

        it.attributeDescriptor = attribute

        if (indexDescriptor != null) {
            it.indexInteractor = context.getIndexInteractor(indexDescriptor)
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
                if (!attribute.type.isPrimitive || attribute.type != it.value!!::class.javaPrimitiveType) {
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

/**
 * Indicates whether the query is a query all type or not
 *
 * @param descriptor Descriptor for entity type to query
 */
fun Query.isDefaultQuery(descriptor: EntityDescriptor): Boolean = this.criteria == null
        || (this.criteria!!.subCriteria.size <= 0 && this.criteria!!.operator === QueryCriteriaOperator.NOT_NULL && this.criteria!!.attribute == descriptor.identifier!!.name)
        || (this.criteria!!.subCriteria.size <= 0 && this.criteria!!.operator === QueryCriteriaOperator.NOT_EQUAL && this.criteria!!.value == null && this.criteria!!.attribute == descriptor.identifier!!.name)
