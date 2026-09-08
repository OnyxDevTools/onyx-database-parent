package com.onyx.vector

import com.onyx.persistence.SearchVectorManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.SearchSupport
import com.onyx.persistence.annotations.SearchVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SearchVectorConfigurationTest {
    @Test
    fun `version and dimensions isolate query spaces and trigger index configuration changes`() {
        val config = SearchVectorConfiguration(2, "bars-v1")
        assertEquals(config.calibrationId, SearchVectorConfiguration(2, "bars-v1").calibrationId)
        assertNotEquals(config.calibrationId, SearchVectorConfiguration(2, "bars-v2").calibrationId)
        assertNotEquals(config.calibrationId, SearchVectorConfiguration(3, "bars-v1").calibrationId)
        assertEquals(config.calibrationId, config.query(floatArrayOf(1f, 0f)).calibrationId)
        assertNotEquals(
            VectorManagedConfiguration.forClass(FirstVersion::class.java).configurationId,
            VectorManagedConfiguration.forClass(SecondVersion::class.java).configurationId,
        )
    }

    @Test
    fun `query rejects mismatched incomplete nonfinite and zero vectors`() {
        val config = SearchVectorConfiguration(2, "bars-v1")
        for (vector in listOf(floatArrayOf(1f), floatArrayOf(0f, 0f), floatArrayOf(1f, Float.NaN),
            floatArrayOf(1f, Float.POSITIVE_INFINITY))) {
            assertFailsWith<IllegalArgumentException> { config.query(vector) }
        }
        assertFailsWith<IllegalArgumentException> { SearchVectorConfiguration(0, "bars-v1") }
        assertFailsWith<IllegalArgumentException> { SearchVectorConfiguration(2, " ") }
        assertFailsWith<IllegalArgumentException> {
            VectorManagedConfiguration.forClass(LexicalOnly::class.java)
        }
    }

    @Entity
    @SearchVector(dimensions = 2, version = "bars-v1")
    private class FirstVersion : SearchVectorManagedEntity() {
        @Identifier var id: Long = 0
    }

    @Entity
    @SearchVector(dimensions = 2, version = "bars-v2")
    private class SecondVersion : SearchVectorManagedEntity() {
        @Identifier var id: Long = 0
    }

    @Entity(searchSupport = SearchSupport.LEXICAL)
    @SearchVector(dimensions = 2, version = "bars-v1")
    private class LexicalOnly : SearchVectorManagedEntity() {
        @Identifier var id: Long = 0
    }
}
