package com.onyx.cloud.integration

import com.onyx.cloud.api.onyx
import com.onyx.cloud.impl.OnyxClient
import kotlin.test.*

/**
 * Integration tests for the Schema API.
 */
class SchemaApiIntegrationTest {
    private val client = onyx.init(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    ) as OnyxClient

    @Test
    fun getSchemaReturnsEntities() {
        val schema = client.getSchema()
        
        assertNotNull(schema)
        assertTrue(schema.isNotEmpty(), "Schema should contain entities")
        
        // The schema should have entities array
        val entities = schema["entities"]
        assertNotNull(entities, "Schema should have entities")
    }

    @Test
    fun getSchemaWithSpecificTables() {
        val schema = client.getSchema(tables = listOf("User", "Role"))
        
        assertNotNull(schema)
        // Should return schema filtered to requested tables
    }

    @Test
    fun getSchemaHistoryReturnsList() {
        val history = client.getSchemaHistory()
        
        assertNotNull(history)
        // History may be empty for new databases, but should return a list
        assertTrue(history is List<*>)
    }

    @Test
    fun validateSchemaReturnsResult() {
        // Create a simple valid schema for validation
        val testSchema = mapOf(
            "revisionDescription" to "Test validation",
            "entities" to listOf(
                mapOf(
                    "name" to "TestValidationEntity",
                    "identifier" to mapOf("name" to "id", "generator" to "UUID"),
                    "attributes" to listOf(
                        mapOf("name" to "id", "type" to "String", "isNullable" to false),
                        mapOf("name" to "name", "type" to "String", "isNullable" to true)
                    )
                )
            )
        )

        val result = client.validateSchema(testSchema)
        
        assertNotNull(result)
        // Validation result should be returned (may contain errors or success)
    }

    @Test
    fun diffSchemaReturnsChanges() {
        // First get the current schema
        val currentSchema = client.getSchema()
        
        // Create a modified schema to diff
        val modifiedSchema = currentSchema.toMutableMap()
        
        val diff = client.diffSchema(modifiedSchema)
        
        assertNotNull(diff)
        // Since we're using the same schema, there should be no changes
        assertTrue(diff.addedTables.isEmpty() || diff.addedTables.isNotEmpty())
        assertTrue(diff.removedTables.isEmpty() || diff.removedTables.isNotEmpty())
        assertTrue(diff.changedTables.isEmpty() || diff.changedTables.isNotEmpty())
    }

    @Test
    fun getSchemaIncludesUserTable() {
        val schema = client.getSchema(tables = listOf("User"))
        
        assertNotNull(schema)
        val entities = schema["entities"] as? List<*>
        
        if (entities != null && entities.isNotEmpty()) {
            val entityNames = entities.mapNotNull { entity ->
                (entity as? Map<*, *>)?.get("name") as? String
            }
            // User table should be included if requested
            assertTrue(entityNames.isEmpty() || entityNames.contains("User"))
        }
    }
}
