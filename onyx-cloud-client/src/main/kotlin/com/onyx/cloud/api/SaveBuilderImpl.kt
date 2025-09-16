package com.onyx.cloud.api

/**
 * Default implementation of [ISaveBuilder] to support cascading save operations.
 *
 * @param delegate Underlying database instance to execute save.
 * @param table Target table name.
 * @param relationships Internal list of relationship strings to cascade.
 */
internal class SaveBuilderImpl(
    private val delegate: IOnyxDatabase<Any>,
    private val table: String,
    private val relationships: MutableList<String> = mutableListOf()
) : ISaveBuilder<Any> {
    /**
     * Specifies relationships to cascade when saving.
     *
     * @param relationships Relationship strings to include.
     * @return This builder for chaining.
     */
    override fun cascade(vararg relationships: String): ISaveBuilder<Any> {
        this.relationships.addAll(relationships.asList())
        return this
    }

    /**
     * Persists a single entity with configured cascade relationships.
     *
     * @param entity Entity map to persist.
     * @return Result of the save operation.
     */
    override fun one(entity: Map<String, Any?>): Any? =
        delegate.save(table, entity, SaveOptions(relationships.toList()))

    /**
     * Persists multiple entities with configured cascade relationships.
     *
     * @param entities List of entity maps to persist.
     * @return Result of the save operation.
     */
    override fun many(entities: List<Map<String, Any?>>): Any? =
        delegate.save(table, entities, SaveOptions(relationships.toList()))
}
