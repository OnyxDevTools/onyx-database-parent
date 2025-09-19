package com.onyx.cloud.impl

import com.onyx.cloud.api.DeleteOptions
import com.onyx.cloud.api.ICascadeBuilder
import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.SaveOptions

/**
 * Default implementation of [com.onyx.cloud.api.ICascadeBuilder] to support cascading save and delete operations.
 *
 * @param delegate Underlying database instance to execute operations.
 * @param relationships Initial list of relationship strings to cascade.
 */
class CascadeBuilderImpl<Schema : Any>(
    val delegate: IOnyxDatabase<Schema>,
    relationships: List<String>
) : ICascadeBuilder {
    val cascadeRelationships: MutableList<String> = relationships.toMutableList()

    /**
     * Specifies relationships to cascade through.
     *
     * @param relationships Relationship strings to include.
     * @return This builder for chaining.
     */
    override fun cascade(vararg relationships: String): ICascadeBuilder {
        cascadeRelationships.addAll(relationships.asList())
        return this
    }

    override fun <T> save(entityOrEntities: Any): T {
        val type = if (entityOrEntities is List<*>) {
            entityOrEntities.first()!!::class
        } else {
            entityOrEntities::class
        }
        @Suppress("UNCHECKED_CAST")
        return delegate.save(type, entityOrEntities, SaveOptions(relationships = this.cascadeRelationships)) as T
    }

    override fun delete(table: String, primaryKey: String): Boolean =
        delegate.delete(table, primaryKey, DeleteOptions(relationships = this.cascadeRelationships))
}