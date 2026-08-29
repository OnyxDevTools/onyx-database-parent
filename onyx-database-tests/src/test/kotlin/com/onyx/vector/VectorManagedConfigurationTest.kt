package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.InvalidIndexException
import com.onyx.extension.validate
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.annotations.values.IndexType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VectorManagedConfigurationTest {

    @Test
    fun entityEntropyScalesOnceAndConfiguresTheInheritedVectorIndex() {
        val configuration = VectorManagedConfiguration.forClass(ScaledEntropyEntity::class.java)
        val descriptor = EntityDescriptor(ScaledEntropyEntity::class.java)
        val index = descriptor.indexes.getValue(VectorManagedEntity.REPRESENTATION_FIELD)

        assertEquals(65, ScaledEntropyEntity::class.java.getAnnotation(Entity::class.java).entropy)
        assertEquals(128, configuration.entropy.bitCount)
        assertEquals(setOf("id", "label"), configuration.attributes.mapTo(linkedSetOf()) { it.name })
        assertTrue(configuration.attributes.all { it.families == setOf(VectorFeatureFamily.CATEGORICAL) })
        assertEquals(IndexType.VECTOR, index.indexType)
        assertEquals(128, index.entropy)
        assertEquals(configuration.configurationId, index.configurationId)
        assertEquals(configuration.signature, index.configurationSignature)
    }

    @Test
    fun fieldModesResolveToCanonicalFeatureFamilies() {
        val configuration = VectorManagedConfiguration.forClass(ControlledFeaturesEntity::class.java)
        val definitions = configuration.attributes.associateBy(VectorAttributeDefinition::name)

        assertEquals(setOf("automatic", "intervalOnly", "termsAndGrams", "universal"), definitions.keys)
        assertEquals(
            setOf(VectorFeatureFamily.CATEGORICAL),
            definitions.getValue("automatic").families
        )
        assertEquals(
            setOf(VectorFeatureFamily.INTERVAL),
            definitions.getValue("intervalOnly").families
        )
        assertEquals(
            setOf(VectorFeatureFamily.TEXT_TERM, VectorFeatureFamily.TEXT_NGRAM),
            definitions.getValue("termsAndGrams").families
        )
        assertEquals(VectorFeatureFamily.entries.toSet(), definitions.getValue("universal").families)
        assertTrue(configuration.supports(VectorFeatureFamily.TEXT_TERM))
    }

    @Test
    fun selectedFamilyOrderIsCanonicalInTheFrozenSignature() {
        val first = VectorManagedConfiguration.forClass(SelectedOrderOneEntity::class.java)
        val second = VectorManagedConfiguration.forClass(SelectedOrderTwoEntity::class.java)

        assertEquals(first.signature, second.signature)
        assertEquals(first.configurationId, second.configurationId)
    }

    @Test
    fun retiredIndexOrdinalCannotBeUsedByNewEntities() {
        val failure = assertFailsWith<InvalidIndexException> {
            EntityDescriptor(RetiredIndexEntity::class.java).validate()
        }

        assertTrue(failure.message.orEmpty().contains("has been retired"))
    }

    @Entity(entropy = 65)
    private class ScaledEntropyEntity : VectorManagedEntity() {
        @Identifier
        var id: Long = 0L

        @Attribute
        var label: String = ""
    }

    @Entity
    private class ControlledFeaturesEntity : VectorManagedEntity() {
        @Identifier
        @VectorAttribute(mode = VectorAttributeMode.IGNORE)
        var id: Long = 0L

        @Attribute
        var automatic: String = ""

        @Attribute
        @VectorAttribute(
            mode = VectorAttributeMode.SELECTED,
            families = [VectorFeatureFamily.INTERVAL]
        )
        var intervalOnly: Int = 0

        @Attribute
        @VectorAttribute(
            mode = VectorAttributeMode.SELECTED,
            families = [VectorFeatureFamily.TEXT_NGRAM, VectorFeatureFamily.TEXT_TERM]
        )
        var termsAndGrams: String = ""

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var universal: String = ""

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.IGNORE)
        var ignored: String = ""
    }

    @Entity
    private class SelectedOrderOneEntity : VectorManagedEntity() {
        @Identifier
        @VectorAttribute(mode = VectorAttributeMode.IGNORE)
        var id: Long = 0L

        @Attribute
        @VectorAttribute(
            mode = VectorAttributeMode.SELECTED,
            families = [VectorFeatureFamily.TEXT_TERM, VectorFeatureFamily.INTERVAL]
        )
        var value: String = ""
    }

    @Entity
    private class SelectedOrderTwoEntity : VectorManagedEntity() {
        @Identifier
        @VectorAttribute(mode = VectorAttributeMode.IGNORE)
        var id: Long = 0L

        @Attribute
        @VectorAttribute(
            mode = VectorAttributeMode.SELECTED,
            families = [VectorFeatureFamily.INTERVAL, VectorFeatureFamily.TEXT_TERM]
        )
        var value: String = ""
    }

    @Suppress("DEPRECATION")
    @Entity
    private class RetiredIndexEntity : ManagedEntity() {
        @Identifier
        var id: Long = 0L

        @Attribute
        @Index(type = IndexType.RETIRED)
        var value: String = ""
    }
}
