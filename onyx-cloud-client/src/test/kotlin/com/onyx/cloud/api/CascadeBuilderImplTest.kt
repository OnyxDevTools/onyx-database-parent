package com.onyx.cloud.api

import kotlin.test.*
import kotlin.reflect.KClass

/**
 * Unit tests for [com.onyx.cloud.impl.CascadeBuilderImpl] verifying cascade save and delete behavior.
 */
class CascadeBuilderImplTest {
    private data class SaveCaptured(val table: KClass<*>, val entity: Any, val options: SaveOptions?)
    private data class DeleteCaptured(val table: String, val key: String, val options: DeleteOptions?)

    @Test
    fun `save() invokes underlying save with configured cascade relationships`() {
        val saveCalls = mutableListOf<SaveCaptured>()
        val stubDb = object : IOnyxDatabase<Any> {

            override fun select(vararg fields: String): IQueryBuilder =
                throw UnsupportedOperationException()

            override fun search(queryText: String, minScore: Float?): IQueryBuilder {
                TODO("Not yet implemented")
            }

            // Use default cascade() from the interface (not overridden).

            override fun <T> save(
                table: KClass<*>,
                entityOrEntities: T,
                options: SaveOptions?
            ): T {
                saveCalls.add(SaveCaptured(table, entityOrEntities as Any, options))
                @Suppress("UNCHECKED_CAST")
                return entityOrEntities as T
            }

            override fun <T> findById(
                table: KClass<*>,
                primaryKey: Any,
                options: FindOptions?
            ): T? = throw UnsupportedOperationException()

            override fun delete(
                table: String,
                primaryKey: String,
                options: DeleteOptions?
            ): Boolean = throw UnsupportedOperationException()

            override fun saveDocument(doc: OnyxDocument): OnyxDocument =
                throw UnsupportedOperationException()

            override fun getDocument(documentId: String, options: DocumentOptions?): Any? =
                throw UnsupportedOperationException()

            override fun deleteDocument(documentId: String): Any? =
                throw UnsupportedOperationException()

            override fun close() = throw UnsupportedOperationException()
        }

        val builder = stubDb.cascade("r2", "r3")

        val entity = mapOf("id" to 10)

        // ICascadeBuilder.save(entity) – no table arg now; assert relationships forwarded.
        val result: Map<String, Any> = builder.save(entity)

        assertEquals(entity, result)
        assertEquals(1, saveCalls.size)

        with(saveCalls.first()) {
            // Table is inferred by the builder; for a Map entity this will be Map::class.
            assertEquals(entity, this.entity)
            assertEquals(listOf("r2", "r3"), options?.relationships)
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
            ): Boolean {
                deleteCalls.add(DeleteCaptured(table, primaryKey, options))
                return true
            }

            // Unused in this test:
            override fun select(vararg fields: String): IQueryBuilder =
                throw UnsupportedOperationException()

            override fun search(queryText: String, minScore: Float?): IQueryBuilder {
                TODO("Not yet implemented")
            }

            // Use default cascade() from the interface (not overridden).

            override fun <T> save(
                table: KClass<*>,
                entityOrEntities: T,
                options: SaveOptions?
            ): T = entityOrEntities

            override fun <T> findById(
                table: KClass<*>,
                primaryKey: Any,
                options: FindOptions?
            ): T? = throw UnsupportedOperationException()

            override fun saveDocument(doc: OnyxDocument): OnyxDocument =
                throw UnsupportedOperationException()

            override fun getDocument(documentId: String, options: DocumentOptions?): Any? =
                throw UnsupportedOperationException()

            override fun deleteDocument(documentId: String): Any? =
                throw UnsupportedOperationException()

            override fun close() = throw UnsupportedOperationException()
        }

        val builder = stubDb.cascade("x")

        val ok: Boolean = builder.delete("T", "key1")
        assertTrue(ok)

        assertEquals(1, deleteCalls.size)
        with(deleteCalls.first()) {
            assertEquals("T", table)
            assertEquals("key1", key)
            assertEquals(listOf("x"), options?.relationships)
        }
    }
}
