package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.VectorFeatureFamily
import java.text.Normalizer
import java.util.Locale

/** Builds the configured sparse feature representation stored by [VectorManagedEntity]. */
object VectorEntityEncoder {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+(?:[._-][\\p{L}\\p{N}]+)*")

    fun encode(
        entity: VectorManagedEntity,
        descriptor: EntityDescriptor,
        existing: VectorRepresentation? = null
    ): VectorRepresentation = prepare(entity, descriptor, existing).representation

    internal fun prepare(
        entity: VectorManagedEntity,
        descriptor: EntityDescriptor,
        existing: VectorRepresentation? = null
    ): PreparedVectorRepresentation {
        val configuration = VectorManagedConfiguration.forClass(descriptor.entityClass)
        val logicalFeatures = LinkedHashSet<String>()
        val namespace = namespace(descriptor, configuration)
        logicalFeatures += "$namespace/type:${descriptor.entityClass.name}"

        configuration.attributes.forEach { definition ->
            val attribute = descriptor.attributes[definition.name] ?: return@forEach
            val value = attribute.field.get(entity)
            encodeAttribute(namespace, definition, value, configuration, logicalFeatures)
        }

        val fingerprints = logicalFeatures
            .map { VectorFeatureHasher.fingerprint(it, configuration.entropy) }
            .distinct()
            .sortedWith(FINGERPRINT_COMPARATOR)
        val words = LongArray(fingerprints.size * configuration.entropy.wordCount)
        val routeKeys = LinkedHashSet<Long>(fingerprints.size)
        var offset = 0
        fingerprints.forEach { fingerprint ->
            fingerprint.toLongArray().copyInto(words, offset)
            offset += fingerprint.wordCount
            routeKeys += fingerprint.routeKey
        }

        val compatibleSemantic = existing?.takeIf {
            // Sparse-family changes intentionally produce a new configuration ID and rebuild the
            // hidden index. Semantic routing remains compatible as long as its bit width matches.
            configuration.searchSupport.supportsSemantic &&
                it.featureHashBits == configuration.entropy.bitCount &&
                it.hasSemanticSignature
        }
        val compatibleHnsw = existing?.takeIf {
            configuration.searchSupport.supportsSemantic && it.hasHnswVector
        }
        return PreparedVectorRepresentation(
            representation = VectorRepresentation(
                encodingVersion = configuration.encodingVersion,
                featureHashBits = configuration.entropy.bitCount,
                configurationId = configuration.configurationId,
                calibrationId = compatibleSemantic?.calibrationId ?: VectorRepresentation.NO_CALIBRATION,
                hnswCalibrationId = compatibleHnsw?.hnswCalibrationId
                    ?: VectorRepresentation.NO_CALIBRATION,
                bucketId = compatibleSemantic?.bucketId ?: VectorRepresentation.NO_BUCKET,
                boundaryConfidence = compatibleSemantic?.boundaryConfidence ?: 0f,
                cells = compatibleSemantic?.cells ?: intArrayOf(),
                cellCounts = compatibleSemantic?.cellCounts ?: intArrayOf(),
                semanticFingerprint = compatibleSemantic?.semanticFingerprint ?: longArrayOf(),
                semanticBands = compatibleSemantic?.semanticBands ?: longArrayOf(),
                hnswVector = compatibleHnsw?.hnswVector ?: byteArrayOf(),
                featureWords = words
            ),
            featureRouteKeys = routeKeys.toLongArray()
        )
    }

    /** Whether this entity type selected at least one field for whole-record term search. */
    fun hasSearchableTextFields(descriptor: EntityDescriptor): Boolean {
        val configuration = VectorManagedConfiguration.forClass(descriptor.entityClass)
        return configuration.searchableTextAttributes.any(descriptor.attributes::containsKey)
    }

    /** Stable text submitted to an application's automatic embedding provider. */
    fun searchableText(
        entity: VectorManagedEntity,
        descriptor: EntityDescriptor,
    ): String {
        val configuration = VectorManagedConfiguration.forClass(descriptor.entityClass)
        return configuration.searchableTextAttributes.asSequence()
            .mapNotNull { attributeName ->
                val value = descriptor.attributes[attributeName]?.field?.get(entity)
                    ?: return@mapNotNull null
                searchableValueText(value).takeIf(String::isNotBlank)
            }
            .joinToString("\n")
    }

    /** Mirrors scalar text ingestion without leaking array identities or unordered container order. */
    private fun searchableValueText(value: Any): String = when (value) {
        is Map<*, *> -> value.entries.map { entry ->
            listOfNotNull(entry.key, entry.value)
                .joinToString(" ", transform = ::stableSearchableScalarText)
        }.sorted().joinToString("\n")
        is Set<*> -> value.filterNotNull().map(::stableSearchableScalarText).sorted().joinToString("\n")
        is Iterable<*> -> value.filterNotNull().joinToString("\n", transform = ::stableSearchableScalarText)
        is Array<*> -> value.filterNotNull().joinToString("\n", transform = ::stableSearchableScalarText)
        is BooleanArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is ByteArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is ShortArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is IntArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is LongArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is FloatArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is DoubleArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        is CharArray -> value.joinToString("\n", transform = ::stableSearchableScalarText)
        else -> stableSearchableScalarText(value)
    }

    /** Embedding input must not inherit legacy locale or JVM-default-timezone rendering. */
    private fun stableSearchableScalarText(value: Any): String = VectorValueCodec.predicateText(value)

    internal fun namespace(descriptor: EntityDescriptor, configuration: VectorManagedConfiguration): String =
        "onyx-vector/${configuration.encodingVersion}/seed:" +
            java.lang.Long.toUnsignedString(configuration.randomSeed, 16) +
            "/${descriptor.entityClass.name}"

    internal fun fingerprint(
        descriptor: EntityDescriptor,
        configuration: VectorManagedConfiguration,
        logicalSuffix: String
    ): FeatureFingerprint = VectorFeatureHasher.fingerprint(
        "${namespace(descriptor, configuration)}/$logicalSuffix",
        configuration.entropy
    )

    internal fun tokens(value: String): List<String> = tokenPattern
        .findAll(normalizeText(value))
        .map { it.value }
        .toList()

    internal fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    /**
     * Produces the simple, one-code-point-at-a-time fold used by Java's ignore-case comparisons.
     *
     * Unlike Unicode normalization, this preserves substring and prefix boundaries from the raw
     * value. A raw case-sensitive match therefore always has the same folded routing feature, while
     * Java/Kotlin ignore-case matches (including dotless-I cases) share it as well.
     */
    internal fun comparisonFold(value: String): String = buildString(value.length) {
        value.codePoints().forEach { codePoint ->
            appendCodePoint(Character.toLowerCase(Character.toUpperCase(codePoint)))
        }
    }

    /** Whether [value] contains a UTF-16 surrogate that is not part of a complete pair. */
    internal fun hasUnpairedSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val current = value[index]
            when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                        return true
                    }
                    index += 2
                }
                Character.isLowSurrogate(current) -> return true
                else -> index++
            }
        }
        return false
    }

    internal fun intervalFeatureSuffix(
        definition: VectorAttributeDefinition,
        coordinate: IntervalCoordinate,
        node: IntervalNode
    ): String = "attribute:${definition.name}/interval:${coordinate.domain}:${coordinate.bits}/${node.canonicalToken}"

    internal fun categoricalFeatureSuffix(
        definition: VectorAttributeDefinition,
        value: Any
    ): String = "attribute:${definition.name}/category:${escape(VectorValueCodec.categorical(value))}"

    private fun encodeAttribute(
        namespace: String,
        definition: VectorAttributeDefinition,
        value: Any?,
        configuration: VectorManagedConfiguration,
        output: MutableSet<String>
    ) {
        if (value == null) {
            output += "$namespace/attribute:${definition.name}/null"
            return
        }
        output += "$namespace/attribute:${definition.name}/present"

        when (value) {
            is Iterable<*> -> value.filterNotNull().forEach {
                encodeScalar(namespace, definition, it, configuration, output)
            }
            is Array<*> -> value.filterNotNull().forEach {
                encodeScalar(namespace, definition, it, configuration, output)
            }
            is BooleanArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is ByteArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is ShortArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is IntArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is LongArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is FloatArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is DoubleArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is CharArray -> value.forEach { encodeScalar(namespace, definition, it, configuration, output) }
            is Map<*, *> -> value.entries.forEach { entry ->
                entry.key?.let { encodeScalar(namespace, definition, it, configuration, output) }
                entry.value?.let { encodeScalar(namespace, definition, it, configuration, output) }
            }
            else -> encodeScalar(namespace, definition, value, configuration, output)
        }
    }

    private fun encodeScalar(
        namespace: String,
        definition: VectorAttributeDefinition,
        value: Any,
        configuration: VectorManagedConfiguration,
        output: MutableSet<String>
    ) {
        if (definition.supports(VectorFeatureFamily.CATEGORICAL)) {
            output += "$namespace/${categoricalFeatureSuffix(definition, value)}"
        }
        if (definition.supports(VectorFeatureFamily.INTERVAL)) {
            VectorValueCodec.intervalCoordinate(value)?.let { interval ->
                BinaryIntervalTree.path(interval.coordinate, interval.bits).forEach { node ->
                    output += "$namespace/${intervalFeatureSuffix(definition, interval, node)}"
                }
            }
        }

        // Selected text routes are candidate hints; Onyx's ordinary predicate evaluator remains
        // authoritative for case, type, and regex semantics. Comparison routes use a length-
        // preserving Java-compatible fold, while lexical terms retain NFKC normalization. Date
        // predicate text is stable UTC; whole-record terms retain the legacy Date.toString() text.
        val predicateText = VectorValueCodec.predicateText(value)
        val comparisonText = comparisonFold(predicateText)
        if (definition.supports(VectorFeatureFamily.TEXT_EXACT)) {
            output += "$namespace/attribute:${definition.name}/text/exact:${escape(comparisonText)}"
        }
        if (definition.supports(VectorFeatureFamily.TEXT_TERM)) {
            tokens(predicateText).forEach { token ->
                output += "$namespace/attribute:${definition.name}/text/term:${escape(token)}"
            }
            tokens(VectorValueCodec.text(value)).forEach { token ->
                output += "$namespace/text/term:${escape(token)}"
            }
        }
        if (definition.supports(VectorFeatureFamily.TEXT_PREFIX)) {
            encodePrefixes(namespace, definition, comparisonText, configuration.maxTextPrefixLength, output)
        }
        if (definition.supports(VectorFeatureFamily.TEXT_NGRAM)) {
            encodeNGrams(namespace, definition, comparisonText, configuration.textNGramSize, output)
        }
    }

    private fun encodePrefixes(
        namespace: String,
        definition: VectorAttributeDefinition,
        normalized: String,
        maxLength: Int,
        output: MutableSet<String>
    ) {
        if (maxLength <= 0) return
        val codePoints = normalized.codePoints().toArray()
        val prefix = StringBuilder()
        for (index in 0 until minOf(codePoints.size, maxLength)) {
            prefix.appendCodePoint(codePoints[index])
            output += "$namespace/attribute:${definition.name}/text/prefix:${escape(prefix.toString())}"
        }
    }

    private fun encodeNGrams(
        namespace: String,
        definition: VectorAttributeDefinition,
        normalized: String,
        gramSize: Int,
        output: MutableSet<String>
    ) {
        val codePoints = normalized.codePoints().toArray()
        if (codePoints.isEmpty() || gramSize <= 0) return
        for (size in 1..minOf(codePoints.size, gramSize)) {
            for (start in 0..codePoints.size - size) {
                val gram = String(codePoints, start, size)
                output += "$namespace/attribute:${definition.name}/text/gram:${escape(gram)}"
            }
        }
    }

    internal fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '%', '/', ':' -> append('%').append(character.code.toString(16).padStart(2, '0'))
                else -> append(character)
            }
        }
    }

    private val FINGERPRINT_COMPARATOR = Comparator<FeatureFingerprint> { first, second ->
        for (index in 0 until minOf(first.wordCount, second.wordCount)) {
            val comparison = java.lang.Long.compareUnsigned(first[index], second[index])
            if (comparison != 0) return@Comparator comparison
        }
        first.wordCount.compareTo(second.wordCount)
    }
}
