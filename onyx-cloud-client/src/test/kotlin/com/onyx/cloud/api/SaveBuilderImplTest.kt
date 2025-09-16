package com.onyx.cloud.api

import kotlin.test.*

/**
 * Unit tests for [SaveBuilderImpl] verifying cascade behavior.
 */
class SaveBuilderImplTest {
    private data class Captured(val table: String, val entity: Any, val options: SaveOptions?)

    @Test
    fun `one() invokes save with correct cascade relationships`() {
        val captured = mutableListOf<Captured>()
        val stubDb = object : IOnyxDatabase<Any> {
            override fun save(
                table: String,
                entityOrEntities: Any,
                options: SaveOptions?
            ): Any? {
                captured.add(Captured(table, entityOrEntities, options))
                return "ok"
            }
            // Unused interface methods
            override fun from(table: String) = throw UnsupportedOperationException()
            override fun select(vararg fields: String) = throw UnsupportedOperationException()
            override fun cascade(vararg relationships: String) = throw UnsupportedOperationException()
            override fun batchSave(
                table: String,
                entities: List<Any>,
                batchSize: Int,
                options: SaveOptions?
            ) = throw UnsupportedOperationException()
            override fun findById(
                table: String,
                primaryKey: String,
                options: FindOptions?
            ) = throw UnsupportedOperationException()
            override fun delete(
                table: String,
                primaryKey: String,
                options: DeleteOptions?
            ) = throw UnsupportedOperationException()
            override fun saveDocument(doc: OnyxDocument) = throw UnsupportedOperationException()
            override fun getDocument(documentId: String, options: DocumentOptions?) = throw UnsupportedOperationException()
            override fun deleteDocument(documentId: String) = throw UnsupportedOperationException()
            override fun close() = throw UnsupportedOperationException()
        }
        val entity = mapOf("id" to 1)
        val builder = SaveBuilderImpl(stubDb, "User")
            .cascade("rel1", "rel2")
        val result = builder.one(entity)
        assertEquals("ok", result)
        assertEquals(1, captured.size)
        with(captured.first()) {
            assertEquals("User", table)
            assertEquals(entity, this.entity)
            assertEquals(listOf("rel1", "rel2"), options?.relationships)
        }
    }

    @Test
    fun `many() invokes save with correct cascade relationships`() {
        val captured = mutableListOf<Captured>()
        val stubDb = object : IOnyxDatabase<Any> {
            override fun save(
                table: String,
                entityOrEntities: Any,
                options: SaveOptions?
            ): Any? {
                captured.add(Captured(table, entityOrEntities, options))
                return null
            }
            // Unused interface methods
            override fun from(table: String) = throw UnsupportedOperationException()
            override fun select(vararg fields: String) = throw UnsupportedOperationException()
            override fun cascade(vararg relationships: String) = throw UnsupportedOperationException()
            override fun batchSave(
                table: String,
                entities: List<Any>,
                batchSize: Int,
                options: SaveOptions?
            ) = throw UnsupportedOperationException()
            override fun findById(
                table: String,
                primaryKey: String,
                options: FindOptions?
            ) = throw UnsupportedOperationException()
            override fun delete(
                table: String,
                primaryKey: String,
                options: DeleteOptions?
            ) = throw UnsupportedOperationException()
            override fun saveDocument(doc: OnyxDocument) = throw UnsupportedOperationException()
            override fun getDocument(documentId: String, options: DocumentOptions?) = throw UnsupportedOperationException()
            override fun deleteDocument(documentId: String) = throw UnsupportedOperationException()
            override fun close() = throw UnsupportedOperationException()
        }
        val entities = listOf(mapOf("id" to 1), mapOf("id" to 2))
        val builder = SaveBuilderImpl(stubDb, "User").cascade("r")
        builder.many(entities)
        assertEquals(1, captured.size)
        with(captured.first()) {
            assertEquals("User", table)
            assertEquals(entities, this.entity)
            assertEquals(listOf("r"), options?.relationships)
        }
    }
}
