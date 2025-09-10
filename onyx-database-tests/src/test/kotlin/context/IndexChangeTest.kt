package com.onyx.persistence.context.impl

import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemIndex
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class IndexChangeTest {
    private class TestSchemaContext : DefaultSchemaContext() {
        val rebuilt = mutableListOf<String>()
        public override fun rebuildIndex(systemEntity: SystemEntity, indexName: String) {
            rebuilt += indexName
        }
        fun callCheck(old: SystemEntity, new: SystemEntity) {
            checkForIndexChanges(old, new)
        }
    }

    @Test
    fun addingIndexTriggersRebuild() {
        val ctx = TestSchemaContext()
        val oldEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf()
        }
        val newEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf(SystemIndex().apply { name = "newIndex" })
        }

        ctx.callCheck(oldEntity, newEntity)

        assertEquals(listOf("newIndex"), ctx.rebuilt)
    }

    @Test
    fun removingIndexDoesNotTriggerRebuild() {
        val ctx = TestSchemaContext()
        val oldEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf(SystemIndex().apply { name = "oldIndex" })
        }
        val newEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf()
        }

        ctx.callCheck(oldEntity, newEntity)

        assertTrue(ctx.rebuilt.isEmpty())
    }
}

