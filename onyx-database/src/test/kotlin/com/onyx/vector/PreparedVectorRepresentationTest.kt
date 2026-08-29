package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PreparedVectorRepresentationTest {

    private val descriptor = EntityDescriptor(PreparedEntity::class.java)

    @Test
    fun `encoder retains the same deduplicated routes as the persisted representation`() {
        val prepared = VectorEntityEncoder.prepare(
            PreparedEntity().apply {
                id = 7L
                label = "Amber Comet"
            },
            descriptor
        )
        val reconstructed = PreparedVectorRepresentation.fromRepresentation(prepared.representation)

        assertContentEquals(reconstructed.featureRouteKeys, prepared.featureRouteKeys)
        assertEquals(prepared.featureRouteKeys.distinct().size, prepared.featureRouteKeys.size)
        assertEquals(
            prepared.representation,
            VectorRepresentationCodec.decode(VectorRepresentationCodec.encode(prepared.representation))
        )
    }

    @Test
    fun `entity supplies prepared routes once and then falls back to persisted bytes`() {
        val entity = PreparedEntity().apply {
            id = 11L
            label = "route-once"
        }
        val prepared = entity.prepareVectorRepresentation(descriptor)

        assertSame(prepared, assertIs<PreparedVectorRepresentation>(entity.consumePreparedVectorIndexValue()))
        val persisted = assertIs<ByteArray>(entity.consumePreparedVectorIndexValue())
        assertEquals(prepared.representation, VectorRepresentationCodec.decode(persisted))
    }

    @Entity(entropy = 64)
    private class PreparedEntity : VectorManagedEntity() {
        @Identifier
        var id: Long = 0L

        @Attribute
        var label: String = ""
    }
}
