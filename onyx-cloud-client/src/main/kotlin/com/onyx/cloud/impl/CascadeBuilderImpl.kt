package com.onyx.cloud.impl

import com.onyx.cloud.api.DeleteOptions
import com.onyx.cloud.api.ICascadeBuilder
import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.SaveOptions

/**
 * Default [ICascadeBuilder] implementation used to configure cascading behaviour for database operations.
 *
 * The builder collects relationship paths that should cascade when calling [save] or [delete] on the
 * underlying [IOnyxDatabase]. Each call merges additional relationship tokens into the set of
 * relationships that will cascade.
 *
 * @param delegate underlying database instance responsible for executing the final operation.
 * @param relationships initial set of relationship descriptors that should be cascaded.
 */
class CascadeBuilderImpl<Schema : Any>(
    val delegate: IOnyxDatabase<Schema>,
    relationships: List<String>
) : ICascadeBuilder {
    val cascadeRelationships: MutableList<String> = relationships.toMutableList()

    /**
     * Registers additional relationship paths to include in future cascade operations.
     *
     * @param relationships one or more relationship descriptors, typically formatted as
     * `relationship.attribute`.
     * @return the same builder instance to allow fluent chaining.
     */
    override fun cascade(vararg relationships: String): ICascadeBuilder {
        cascadeRelationships.addAll(relationships.asList())
        return this
    }

    /**
     * Persists the provided entity (or collection of entities) with the configured cascade relationships.
     *
     * @param entityOrEntities a single entity instance or a [List] of entities to persist.
     * @return the database response cast to the caller's requested type.
     */
    override fun <T> save(entityOrEntities: Any): T {
        val type = if (entityOrEntities is List<*>) {
            entityOrEntities.first()!!::class
        } else {
            entityOrEntities::class
        }
        @Suppress("UNCHECKED_CAST")
        return delegate.save(type, entityOrEntities, SaveOptions(relationships = this.cascadeRelationships)) as T
    }

    /**
     * Deletes the entity identified by [table] and [primaryKey] using the configured cascade relationships.
     *
     * @param table database table or collection name of the entity.
     * @param primaryKey identifier for the record to delete.
     * @return `true` when the entity was successfully removed; otherwise `false`.
     */
    override fun delete(table: String, primaryKey: String): Boolean =
        delegate.delete(table, primaryKey, DeleteOptions(relationships = this.cascadeRelationships))
}