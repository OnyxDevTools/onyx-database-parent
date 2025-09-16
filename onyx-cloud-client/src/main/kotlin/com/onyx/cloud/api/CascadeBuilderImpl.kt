package com.onyx.cloud.api

/**
 * Default implementation of [ICascadeBuilder] to support cascading save and delete operations.
 *
 * @param delegate Underlying database instance to execute operations.
 * @param relationships Initial list of relationship strings to cascade.
 */
internal class CascadeBuilderImpl<Schema : Any>(
    private val delegate: IOnyxDatabase<Schema>,
    relationships: List<String>
) : ICascadeBuilder<Schema> {
    private val cascadeRelationships: MutableList<String> = relationships.toMutableList()

    /**
     * Specifies relationships to cascade through.
     *
     * @param relationships Relationship strings to include.
     * @return This builder for chaining.
     */
    override fun cascade(vararg relationships: String): ICascadeBuilder<Schema> {
        cascadeRelationships.addAll(relationships.asList())
        return this
    }

    /**
     * Saves one or many entities for a given table with configured cascade relationships.
     *
     * @param table Target table name.
     * @param entityOrEntities Entity or list of entities to save.
     * @return Result of the save operation.
     */
    override fun save(table: String, entityOrEntities: Any): Any? =
        delegate.save(table, entityOrEntities, SaveOptions(cascadeRelationships.toList()))

    /**
     * Deletes an entity by primary key with configured cascade relationships.
     *
     * @param table Target table name.
     * @param primaryKey Primary key of the entity to delete.
     * @return Result of the delete operation.
     */
    override fun delete(table: String, primaryKey: String): Any? =
        delegate.delete(table, primaryKey, DeleteOptions(relationships = cascadeRelationships.toList()))
}
