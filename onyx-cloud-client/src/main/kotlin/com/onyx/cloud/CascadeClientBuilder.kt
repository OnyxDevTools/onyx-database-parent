package com.onyx.cloud

/**
 * Builder for cascading save or delete operations on the client.
 *
 * @param client Underlying HTTP client.
 * @param relationships Relationship graph strings to include as query parameters.
 */
class CascadeClientBuilder(
    private val client: OnyxClient,
    private val relationships: List<String>
) {
    /**
     * Saves one or many entities for a given table, including cascade relationships.
     *
     * @param table Table name.
     * @param entityOrEntities Single entity or list of entities to save.
     * @return Result from the server.
     */
    fun save(table: String, entityOrEntities: Any): Any? =
        client.save(table, entityOrEntities, mapOf("relationships" to relationships.joinToString(",")))

    /**
     * Deletes an entity by primary key, including cascade relationships.
     *
     * @param table Table name.
     * @param primaryKey Entity primary key.
     * @return Success flag from the server.
     */
    fun delete(table: String, primaryKey: String): Boolean =
        client.delete(table, primaryKey, mapOf("relationships" to relationships))
}
