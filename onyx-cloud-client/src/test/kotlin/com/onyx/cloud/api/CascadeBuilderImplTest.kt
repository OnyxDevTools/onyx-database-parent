package com.onyx.cloud.api

import kotlin.test.*

/**
 * Unit tests for [CascadeBuilderImpl] verifying cascade save and delete behavior.
 */
class CascadeBuilderImplTest {
    private data class SaveCaptured(val table: String, val entity: Any, val options: SaveOptions?)
    private data class DeleteCaptured(val table: String, val key: String, val options: DeleteOptions?)

    @Test
    fun `save() invokes underlying save with configured cascade relationships`() {
        val saveCalls = mutableListOf<SaveCaptured>()
        val stubDb = object : IOnyxDatabase<Any> {
            override fun save(
                table: String,
                entityOrEntities: Any,
                options: SaveOptions?
            ): Any? {
                saveCalls.add(SaveCaptured(table, entityOrEntities, options))
                return "saved"
            }
            // Unused
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
        val builder = CascadeBuilderImpl(stubDb, listOf("r1"))
            .cascade("r2", "r3")
        val entity = mapOf("id" to 10)
        val result = builder.save("Entity", entity)
        assertEquals("saved", result)
        assertEquals(1, saveCalls.size)
        with(saveCalls.first()) {
            assertEquals("Entity", table)
            assertEquals(entity, this.entity)
            assertEquals(listOf("r1", "r2", "r3"), options?.relationships)
        }
    }

    @Test
    fun `delete() invokes underlying delete with configured cascade relationships`() {
        val deleteCalls = mutableListOf<DeleteCaptured>()
        val stubDb = object : IOnyxDatabase<Any> {
            override fun delete(
                table: String,
                primaryKey: String,
                options: DeleteOptions?
            ): Any? {
                deleteCalls.add(DeleteCaptured(table, primaryKey, options))
                return "deleted"
            }
            // Unused
            override fun from(table: String) = throw UnsupportedOperationException()
            override fun select(vararg fields: String) = throw UnsupportedOperationException()
            override fun cascade(vararg relationships: String) = throw UnsupportedOperationException()
            override fun save(table: String) = throw UnsupportedOperationException()
            override fun save(
                table: String,
                entityOrEntities: Any,
                options: SaveOptions?
            ) = throw UnsupportedOperationException()
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
            override fun saveDocument(doc: OnyxDocument) = throw UnsupportedOperationException()
            override fun getDocument(documentId: String, options: DocumentOptions?) = throw UnsupportedOperationException()
            override fun deleteDocument(documentId: String) = throw UnsupportedOperationException()
            override fun close() = throw UnsupportedOperationException()
        }
        val builder = CascadeBuilderImpl(stubDb, emptyList())
            .cascade("x")
        val result = builder.delete("T", "key1")
        assertEquals("deleted", result)
        assertEquals(1, deleteCalls.size)
        with(deleteCalls.first()) {
            assertEquals("T", table)
            assertEquals("key1", key)
            assertEquals(listOf("x"), options?.relationships)
        }
    }
}
