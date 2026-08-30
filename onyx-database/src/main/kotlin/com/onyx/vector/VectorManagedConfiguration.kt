package com.onyx.vector

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.SearchSupport
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Frozen entity-level settings shared by record and query encoders. */
data class VectorManagedConfiguration @JvmOverloads constructor(
    val entropy: VectorEntropy,
    val attributes: List<VectorAttributeDefinition>,
    val configurationId: Long,
    val signature: String,
    val searchSupport: SearchSupport = SearchSupport.BOTH,
    internal val searchableTextAttributes: List<String> = attributes.asSequence()
        .filter { it.supports(VectorFeatureFamily.TEXT_TERM) }
        .map(VectorAttributeDefinition::name)
        .toList(),
) {
    internal val randomSeed: Long
        get() = RANDOM_SEED
    internal val encodingVersion: Int
        get() = ENCODING_VERSION
    internal val textNGramSize: Int
        get() = TEXT_NGRAM_SIZE
    internal val maxTextPrefixLength: Int
        get() = MAX_TEXT_PREFIX_LENGTH

    /** Whether at least one included field emits [family]. */
    fun supports(family: VectorFeatureFamily): Boolean = attributes.any { it.supports(family) }

    companion object {
        private const val RANDOM_SEED: Long = 7_640_891_576_956_012_809L
        private const val ENCODING_VERSION: Int = 1
        // Kept separate from the persisted codec/index generation: changing comparison routing
        // clears and rebuilds the existing maps instead of orphaning a prior index generation.
        private const val COMPARISON_ROUTING_VERSION: Int = 2
        private const val TEXT_NGRAM_SIZE: Int = 3
        private const val MAX_TEXT_PREFIX_LENGTH: Int = 32

        private val CONFIGURATIONS = object : ClassValue<VectorManagedConfiguration>() {
            override fun computeValue(type: Class<*>): VectorManagedConfiguration = create(type)
        }

        /** Returns the immutable configuration cached for one loaded entity class. */
        fun forClass(entityClass: Class<*>): VectorManagedConfiguration = CONFIGURATIONS.get(entityClass)

        private fun create(entityClass: Class<*>): VectorManagedConfiguration {
            require(VectorManagedEntity::class.java.isAssignableFrom(entityClass)) {
                "${entityClass.name} does not extend ${VectorManagedEntity::class.java.name}"
            }
            val entity = requireNotNull(entityClass.getAnnotation(Entity::class.java)) {
                "${entityClass.name} is not annotated with ${Entity::class.java.name}"
            }
            val entropy = VectorEntropy(entity.entropy)
            val searchSupport = entity.searchSupport

            val declaredDefinitions = hierarchyFields(entityClass)
                .filter { field ->
                    field.name != VectorManagedEntity.REPRESENTATION_FIELD &&
                        (field.isAnnotationPresent(Attribute::class.java) ||
                            field.isAnnotationPresent(Identifier::class.java) ||
                            field.isAnnotationPresent(Partition::class.java))
                }
                .mapNotNull { field ->
                    val families = resolveFamilies(field.getAnnotation(VectorAttribute::class.java))
                        ?: return@mapNotNull null
                    VectorAttributeDefinition(field.name, field.type.name, families)
                }
                .sortedBy { it.name }
            val searchableTextAttributes = declaredDefinitions.asSequence()
                .filter { it.supports(VectorFeatureFamily.TEXT_TERM) }
                .map(VectorAttributeDefinition::name)
                .toList()
            val definitions = if (searchSupport.supportsLexical) {
                declaredDefinitions
            } else {
                // TEXT_TERM doubles as the declaration of embedding-input fields. Semantic-only
                // entities retain that declaration without emitting lexical routes or allowing
                // the fingerprint planner to route lexical predicates through an empty index.
                declaredDefinitions.map { definition ->
                    definition.copy(families = definition.families - VectorFeatureFamily.TEXT_TERM)
                }
            }

            val signature = buildString {
                append("vector-managed-v").append(ENCODING_VERSION)
                append('|').append(entropy.bitCount)
                append('|').append(RANDOM_SEED)
                append('|').append(TEXT_NGRAM_SIZE)
                append('|').append(MAX_TEXT_PREFIX_LENGTH)
                append('|').append(COMPARISON_ROUTING_VERSION)
                // Preserve the existing BOTH configuration ID. Explicit narrower capabilities
                // get distinct IDs so schema startup rebuilds and removes the disabled routes.
                if (searchSupport != SearchSupport.BOTH) {
                    append("|searchSupport:").append(searchSupport.name)
                }
                if (!searchSupport.supportsLexical) {
                    // Effective semantic-only families omit TEXT_TERM, so retain its source-field
                    // declaration in the signature to make input changes trigger migration.
                    append("|searchText:").append(searchableTextAttributes.joinToString(","))
                }
                definitions.forEach { append('|').append(it.signature) }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray(StandardCharsets.UTF_8))
            val configurationId = ByteBuffer.wrap(digest).long.let { if (it == 0L) 1L else it }

            return VectorManagedConfiguration(
                entropy,
                definitions,
                configurationId,
                signature,
                searchSupport,
                searchableTextAttributes,
            )
        }

        private fun hierarchyFields(entityClass: Class<*>): List<Field> {
            val result = LinkedHashMap<String, Field>()
            var current: Class<*>? = entityClass
            while (current != null && current != Any::class.java) {
                current.declaredFields.forEach { result.putIfAbsent(it.name, it) }
                current = current.superclass
            }
            return result.values.toList()
        }

        private fun resolveFamilies(annotation: VectorAttribute?): Set<VectorFeatureFamily>? {
            val mode = annotation?.mode ?: VectorAttributeMode.AUTO
            val selected = annotation?.families.orEmpty()
            require(mode == VectorAttributeMode.SELECTED || selected.isEmpty()) {
                "Vector feature families may only be supplied when mode is SELECTED"
            }
            return when (mode) {
                VectorAttributeMode.AUTO -> AUTO_FAMILIES
                VectorAttributeMode.SELECTED -> selected.toCollection(linkedSetOf())
                VectorAttributeMode.UNIVERSAL -> UNIVERSAL_FAMILIES
                VectorAttributeMode.IGNORE -> null
            }
        }

        private val AUTO_FAMILIES: Set<VectorFeatureFamily> =
            setOf(VectorFeatureFamily.CATEGORICAL)
        private val UNIVERSAL_FAMILIES: Set<VectorFeatureFamily> =
            VectorFeatureFamily.entries.toSet()
    }
}

data class VectorAttributeDefinition(
    val name: String,
    val typeName: String,
    val families: Set<VectorFeatureFamily>
) {
    fun supports(family: VectorFeatureFamily): Boolean = family in families

    val signature: String
        get() = "$name:$typeName:${families.map(VectorFeatureFamily::name).sorted().joinToString(",")}"
}
