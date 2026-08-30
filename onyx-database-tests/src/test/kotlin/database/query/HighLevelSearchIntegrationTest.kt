package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.PreInsert
import com.onyx.persistence.annotations.SearchSupport
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.exception.SearchEmbeddingUnavailableException
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.SearchMatch
import com.onyx.persistence.query.SearchMode
import com.onyx.persistence.query.SearchOptions
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.search
import com.onyx.vector.FeatureFingerprint
import com.onyx.vector.SearchEmbedding
import com.onyx.vector.SearchEmbeddingProvider
import com.onyx.vector.VectorEntityEncoder
import com.onyx.vector.VectorFeatureHasher
import com.onyx.vector.VectorManagedConfiguration
import com.onyx.vector.VectorRepresentation
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HighLevelSearchIntegrationTest {
    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private val embeddedTexts = mutableListOf<String>()

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-high-level-search-")
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = databaseDirectory.toString(),
            instance = databaseDirectory.toString(),
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.IN_MEMORY
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
        manager.searchEmbeddingProvider = SearchEmbeddingProvider { text, _ ->
            embeddedTexts += text
            SearchEmbedding(CALIBRATION, embeddingFor(text))
        }
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::databaseDirectory.isInitialized) databaseDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `automatic embeddings support lexical semantic hybrid and scalar filter composition`() {
        val target = save(
            title = "Operating guide",
            body = "The cost per horse is calculated monthly.",
            category = "active",
            note = "allowed",
        )
        val lexicalOnly = save(
            title = "Literal wording",
            body = "Expense for each animal",
            category = "active",
            note = "allowed",
        )
        save(
            title = "Archived guide",
            body = "The cost per horse was retired.",
            category = "archived",
            note = "blocked",
        )

        assertTrue(target.vectorRepresentation()?.hasHnswVector == true)

        val lexical = manager.from<HighLevelSearchEntity>()
            .search(
                "how do i calculate cost per horse",
                SearchOptions(
                    mode = SearchMode.LEXICAL,
                    match = SearchMatch.ANY,
                    minScore = 0.4f,
                ),
            )
            .list<HighLevelSearchEntity>()
        assertTrue(lexical.any { it.id == target.id })
        assertFalse(lexical.any { it.id == lexicalOnly.id })

        val semantic = manager.from<HighLevelSearchEntity>()
            .search(
                "expense for each animal",
                SearchOptions(mode = SearchMode.SEMANTIC, minScore = 0.9f),
            )
            .list<HighLevelSearchEntity>()
        assertTrue(semantic.any { it.id == target.id })
        assertFalse(semantic.any { it.id == lexicalOnly.id })

        val hybrid = manager.from<HighLevelSearchEntity>()
            .search(
                "expense for each animal",
                SearchOptions(mode = SearchMode.HYBRID, minScore = 0.9f, maxCandidates = 20),
            )
            .list<HighLevelSearchEntity>()
        assertTrue(hybrid.any { it.id == target.id })
        assertTrue(hybrid.any { it.id == lexicalOnly.id })

        val options = SearchOptions(mode = SearchMode.SEMANTIC, minScore = 0.9f)
        val filterThenSearch = manager.from<HighLevelSearchEntity>()
            .where("note" eq "allowed")
            .search("expense for each animal", options)
            .list<HighLevelSearchEntity>()
            .map(HighLevelSearchEntity::id)
            .toSet()
        val searchThenFilter = manager.from<HighLevelSearchEntity>()
            .search("expense for each animal", options)
            .and("note" eq "allowed")
            .list<HighLevelSearchEntity>()
            .map(HighLevelSearchEntity::id)
            .toSet()

        assertEquals(setOf(target.id), filterThenSearch)
        assertEquals(filterThenSearch, searchThenFilter)
    }

    @Test
    fun `lexical support rejects semantic modes without embedding or storing HNSW`() {
        val embeddingCallsBeforeSave = embeddedTexts.size
        val entity = manager.saveEntity(
            LexicalHighLevelSearchEntity().apply {
                body = "literal pasture maintenance guide"
            },
        )

        assertEquals(embeddingCallsBeforeSave, embeddedTexts.size)
        assertFalse(requireNotNull(entity.vectorRepresentation()).hasHnswVector)
        assertEquals(
            listOf(entity.id),
            manager.from<LexicalHighLevelSearchEntity>()
                .search(
                    "literal pasture",
                    SearchOptions(mode = SearchMode.LEXICAL, match = SearchMatch.ALL),
                )
                .list<LexicalHighLevelSearchEntity>()
                .map(LexicalHighLevelSearchEntity::id),
        )
        assertEquals(embeddingCallsBeforeSave, embeddedTexts.size)

        assertUnsupportedSearch(SearchMode.SEMANTIC) {
            manager.from<LexicalHighLevelSearchEntity>()
                .search("expense for each animal", SearchOptions(mode = SearchMode.SEMANTIC))
                .list<LexicalHighLevelSearchEntity>()
        }
        assertUnsupportedSearch(SearchMode.HYBRID) {
            manager.from<LexicalHighLevelSearchEntity>()
                .search(
                    "expense for each animal",
                    SearchOptions(mode = SearchMode.HYBRID, maxCandidates = 2),
                )
                .list<LexicalHighLevelSearchEntity>()
        }
        assertEquals(embeddingCallsBeforeSave, embeddedTexts.size)
    }

    @Test
    fun `semantic support rejects lexical modes and stores no lexical term routes`() {
        val embeddingCallsBeforeSave = embeddedTexts.size
        val entity = manager.saveEntity(
            SemanticHighLevelSearchEntity().apply {
                body = "The cost per horse is calculated monthly."
            },
        )
        val representation = requireNotNull(entity.vectorRepresentation())

        assertEquals(embeddingCallsBeforeSave + 1, embeddedTexts.size)
        assertTrue(representation.hasHnswVector)
        assertFalse(
            representation.containsFeature(
                lexicalFeature(SemanticHighLevelSearchEntity::class.java, "text/term:cost"),
            ),
            "SEMANTIC support stored a whole-record lexical term route",
        )
        assertFalse(
            representation.containsFeature(
                lexicalFeature(
                    SemanticHighLevelSearchEntity::class.java,
                    "attribute:body/text/term:cost",
                ),
            ),
            "SEMANTIC support stored an attribute lexical term route",
        )

        assertUnsupportedSearch(SearchMode.LEXICAL) {
            manager.from<SemanticHighLevelSearchEntity>()
                .search("cost per horse", SearchOptions(mode = SearchMode.LEXICAL))
                .list<SemanticHighLevelSearchEntity>()
        }
        val callsBeforeRejectedHybrid = embeddedTexts.size
        assertUnsupportedSearch(SearchMode.HYBRID) {
            manager.from<SemanticHighLevelSearchEntity>()
                .search(
                    "expense for each animal",
                    SearchOptions(mode = SearchMode.HYBRID, maxCandidates = 2),
                )
                .list<SemanticHighLevelSearchEntity>()
        }
        assertEquals(callsBeforeRejectedHybrid, embeddedTexts.size)

        assertEquals(
            listOf(entity.id),
            manager.from<SemanticHighLevelSearchEntity>()
                .search(
                    "expense for each animal",
                    SearchOptions(mode = SearchMode.SEMANTIC, minScore = 0.9f),
                )
                .list<SemanticHighLevelSearchEntity>()
                .map(SemanticHighLevelSearchEntity::id),
        )
    }

    @Test
    fun `default support is both and hybrid embeds the query once`() {
        val declared = requireNotNull(HighLevelSearchEntity::class.java.getAnnotation(Entity::class.java))
        assertEquals(SearchSupport.BOTH, declared.searchSupport)
        val target = save(
            title = "Operating guide",
            body = "The cost per horse is calculated monthly.",
            category = "active",
            note = "allowed",
        )
        val queryText = "expense for each animal"
        val queryEmbeddingsBefore = embeddedTexts.count { it == queryText }

        val results = manager.from<HighLevelSearchEntity>()
            .search(
                queryText,
                SearchOptions(mode = SearchMode.HYBRID, minScore = 0.9f, maxCandidates = 4),
            )
            .list<HighLevelSearchEntity>()

        assertEquals(queryEmbeddingsBefore + 1, embeddedTexts.count { it == queryText })
        assertTrue(results.any { it.id == target.id })
    }

    @Test
    fun `entity streaming uses search admission and enforces declared support`() {
        val target = save(
            title = "Operating guide",
            body = "The cost per horse is calculated monthly.",
            category = "active",
            note = "allowed",
        )

        val streamed = manager.from<HighLevelSearchEntity>()
            .search(
                "expense for each animal",
                SearchOptions(mode = SearchMode.SEMANTIC, minScore = 0.9f),
            )
            .stream<HighLevelSearchEntity>()

        assertEquals(listOf(target.id), streamed.map(HighLevelSearchEntity::id))
        assertUnsupportedSearch(SearchMode.SEMANTIC) {
            manager.from<LexicalHighLevelSearchEntity>()
                .search(
                    "expense for each animal",
                    SearchOptions(mode = SearchMode.SEMANTIC),
                )
                .stream<LexicalHighLevelSearchEntity>()
        }
    }

    @Test
    fun `search embeds once when an unrelated vector group is negated`() {
        val target = save(
            title = "Operating guide",
            body = "The cost per horse is calculated monthly.",
            category = "active",
            note = "allowed",
        )
        val queryText = "expense for each animal"
        val queryEmbeddingsBefore = embeddedTexts.count { it == queryText }

        val results = manager.from<HighLevelSearchEntity>()
            .search(
                queryText,
                SearchOptions(mode = SearchMode.HYBRID, minScore = 0.9f, maxCandidates = 4),
            )
            .and(("category" eq "archived").not())
            .list<HighLevelSearchEntity>()

        assertEquals(queryEmbeddingsBefore + 1, embeddedTexts.count { it == queryText })
        assertTrue(results.any { it.id == target.id })
    }

    @Test
    fun `partitioned lexical search shares one global budget and composes filters`() {
        savePartitioned("north", "alpha beta gamma delta", "allowed")
        savePartitioned("north", "alpha beta gamma", "allowed")
        savePartitioned("south", "alpha beta", "allowed")
        val west = savePartitioned("west", "alpha", "blocked")
        savePartitioned("west", "unrelated", "allowed")

        val options = SearchOptions(
            mode = SearchMode.LEXICAL,
            match = SearchMatch.ANY,
            maxCandidates = 3,
        )
        val globallyRanked = manager.from<PartitionedHighLevelSearchEntity>()
            .search("alpha beta gamma delta", options)
            .list<PartitionedHighLevelSearchEntity>()

        assertEquals(3, globallyRanked.size)
        assertEquals(listOf("north", "south", "west"), globallyRanked.map { it.region })
        assertEquals(
            globallyRanked.map { it.body.split(' ').size }.sortedDescending(),
            globallyRanked.map { it.body.split(' ').size },
        )

        val filterThenSearch = manager.from<PartitionedHighLevelSearchEntity>()
            .where("note" eq "allowed")
            .search("alpha beta gamma delta", options)
            .list<PartitionedHighLevelSearchEntity>()
            .map { it.region to it.body }
        val searchThenFilter = manager.from<PartitionedHighLevelSearchEntity>()
            .search("alpha beta gamma delta", options)
            .and("note" eq "allowed")
            .list<PartitionedHighLevelSearchEntity>()
            .map { it.region to it.body }

        assertEquals(
            globallyRanked.filter { it.note == "allowed" }.map { it.region to it.body },
            filterThenSearch,
        )
        assertEquals(filterThenSearch, searchThenFilter)

        val explicitPartition = manager.from<PartitionedHighLevelSearchEntity>()
            .search(
                "alpha beta gamma delta",
                SearchOptions(mode = SearchMode.LEXICAL, maxCandidates = 1),
            )
            .inPartition("west")
            .list<PartitionedHighLevelSearchEntity>()
        assertEquals(listOf(west.body), explicitPartition.map(PartitionedHighLevelSearchEntity::body))

        val lexicalError = assertFailsWith<IllegalArgumentException> {
            manager.from<PartitionedHighLevelSearchEntity>()
                .search(
                    "alpha",
                    SearchOptions(mode = SearchMode.LEXICAL, maxCandidates = 2),
                )
                .list<PartitionedHighLevelSearchEntity>()
        }
        assertTrue(lexicalError.message.orEmpty().contains("cannot cover all 3 partitions"))
        assertTrue(lexicalError.message.orEmpty().contains("explicit partition"))

        val hybridError = assertFailsWith<IllegalArgumentException> {
            manager.from<PartitionedHighLevelSearchEntity>()
                .search(
                    "alpha",
                    SearchOptions(mode = SearchMode.HYBRID, maxCandidates = 5),
                )
                .list<PartitionedHighLevelSearchEntity>()
        }
        assertTrue(hybridError.message.orEmpty().contains("at least 6 candidates"))
    }

    @Test
    fun `partitioned semantic and hybrid search route across every partition`() {
        val semantic = savePartitioned(
            "north",
            "The cost per horse is calculated monthly.",
            "allowed",
        )
        val lexical = savePartitioned("south", "Expense for each animal", "allowed")
        savePartitioned("west", "Unrelated stable maintenance", "allowed")

        val queryText = "expense for each animal"
        val semanticEmbeddingsBefore = embeddedTexts.count { it == queryText }
        val semanticResults = manager.from<PartitionedHighLevelSearchEntity>()
            .search(
                queryText,
                SearchOptions(
                    mode = SearchMode.SEMANTIC,
                    minScore = 0.9f,
                    maxCandidates = 3,
                ),
            )
            .list<PartitionedHighLevelSearchEntity>()
        assertEquals(semanticEmbeddingsBefore + 1, embeddedTexts.count { it == queryText })
        assertEquals(
            listOf(semantic.region to semantic.body),
            semanticResults.map { it.region to it.body },
        )

        val hybridEmbeddingsBefore = embeddedTexts.count { it == queryText }
        val hybridResults = manager.from<PartitionedHighLevelSearchEntity>()
            .search(
                queryText,
                SearchOptions(
                    mode = SearchMode.HYBRID,
                    minScore = 0.9f,
                    maxCandidates = 6,
                ),
            )
            .list<PartitionedHighLevelSearchEntity>()
            .map { it.region to it.body }
            .toSet()
        assertEquals(hybridEmbeddingsBefore + 1, embeddedTexts.count { it == queryText })
        assertEquals(
            setOf(semantic.region to semantic.body, lexical.region to lexical.body),
            hybridResults,
        )
    }

    @Test
    fun `pre-insert callbacks are embedded and semantic search fails closed without a provider`() {
        val callbackEntity = save(
            title = "Callback guide",
            body = CALLBACK_PENDING,
            category = "active",
            note = "allowed",
        )

        assertTrue(embeddedTexts.any { it.contains(CALLBACK_FINAL) })
        assertFalse(embeddedTexts.any { it.contains(CALLBACK_PENDING) })
        assertTrue(
            manager.from<HighLevelSearchEntity>()
                .search(
                    "expense for each animal",
                    SearchOptions(mode = SearchMode.SEMANTIC, minScore = 0.9f),
                )
                .list<HighLevelSearchEntity>()
                .any { it.id == callbackEntity.id },
        )

        manager.searchEmbeddingProvider = null
        val error = assertFailsWith<SearchEmbeddingUnavailableException> {
            manager.from<HighLevelSearchEntity>()
                .search("expense for each animal", SearchOptions(mode = SearchMode.SEMANTIC))
                .list<HighLevelSearchEntity>()
        }
        assertTrue(error.message.orEmpty().contains("SearchEmbeddingProvider"))

        val emptyPartitionedError = assertFailsWith<SearchEmbeddingUnavailableException> {
            manager.from<PartitionedHighLevelSearchEntity>()
                .search("expense for each animal", SearchOptions(mode = SearchMode.SEMANTIC))
                .list<PartitionedHighLevelSearchEntity>()
        }
        assertTrue(emptyPartitionedError.message.orEmpty().contains("SearchEmbeddingProvider"))
    }

    @Test
    fun `all-table semantic search skips entity types unsupported by the provider`() {
        manager.searchEmbeddingProvider = object : SearchEmbeddingProvider {
            override fun supports(entityType: Class<*>): Boolean =
                entityType == HighLevelSearchEntity::class.java

            override fun embed(text: String, entityType: Class<*>): SearchEmbedding {
                require(supports(entityType))
                return SearchEmbedding(CALIBRATION, embeddingFor(text))
            }
        }
        val target = save(
            title = "Operating guide",
            body = "The cost per horse is calculated monthly.",
            category = "active",
            note = "allowed",
        )
        manager.saveEntity(
            UnsupportedHighLevelSearchEntity().apply {
                body = "Unrelated searchable table"
            },
        )
        val options = SearchOptions(
            mode = SearchMode.SEMANTIC,
            minScore = 0.9f,
            maxCandidates = 10,
        )

        assertFailsWith<SearchEmbeddingUnavailableException> {
            manager.from<UnsupportedHighLevelSearchEntity>()
                .search("expense for each animal", options)
                .list<UnsupportedHighLevelSearchEntity>()
        }

        val results = manager.search("expense for each animal", options)
            .limit(10)
            .list()

        assertEquals(listOf(HighLevelSearchEntity::class.java), results.map { it.entityType })
        assertEquals(listOf(target.id), results.map { it.id })
    }

    @Test
    fun `all-table lexical search ranks globally and excludes entities without searchable text`() {
        val strongest = save(
            title = "Alpha beta gamma",
            body = "alpha beta gamma",
            category = "active",
            note = "allowed",
        )
        save(
            title = "Alpha",
            body = "unrelated",
            category = "active",
            note = "allowed",
        )
        repeat(2) {
            manager.saveEntity(
                UnsupportedHighLevelSearchEntity().apply {
                    body = "alpha"
                },
            )
        }
        manager.saveEntity(
            ManualHnswEntity().apply {
                category = "alpha"
            },
        )

        val results = manager.search(
            "alpha",
            SearchOptions(
                mode = SearchMode.LEXICAL,
                match = SearchMatch.ANY,
                maxCandidates = 3,
            ),
        ).limit(3).list()

        assertEquals(3, results.size)
        assertEquals(strongest.id, results.first().id)
        // Stable class-name ordering allocates the uneven budget as 1 then 2.
        assertEquals(1, results.count { it.entityType == HighLevelSearchEntity::class.java })
        assertEquals(
            2,
            results.count { it.entityType == UnsupportedHighLevelSearchEntity::class.java },
        )
        assertTrue(results.all { it.score != null })
        assertTrue(results.zipWithNext().all { (left, right) -> left.score!! >= right.score!! })

        val error = assertFailsWith<IllegalArgumentException> {
            manager.search(
                "alpha",
                SearchOptions(mode = SearchMode.LEXICAL, maxCandidates = 1),
            ).list()
        }
        assertTrue(error.message.orEmpty().contains("cannot cover all 2 eligible tables"))
    }

    @Test
    fun `automatic provider does not erase manual HNSW vectors on non-searchable entities`() {
        val manual = ManualHnswEntity().apply {
            category = "manual"
            hnswVector(floatArrayOf(0f, 0f, 1f), MANUAL_CALIBRATION)
        }

        manager.saveEntity(manual)

        val representation = requireNotNull(manual.vectorRepresentation())
        assertTrue(representation.hasHnswVector)
        assertEquals(MANUAL_CALIBRATION, representation.hnswCalibrationId)
    }

    @Test
    fun `explicit HNSW vector wins over automatic embedding for one searchable entity write`() {
        val manual = HighLevelSearchEntity().apply {
            title = "Manual embedding"
            body = "The cost per horse is calculated monthly."
            category = "active"
            note = "allowed"
            hnswVector(floatArrayOf(0f, 0f, 1f), MANUAL_CALIBRATION)
        }

        manager.saveEntity(manual)

        val explicitRepresentation = requireNotNull(manual.vectorRepresentation())
        assertTrue(explicitRepresentation.hasHnswVector)
        assertEquals(MANUAL_CALIBRATION, explicitRepresentation.hnswCalibrationId)
        assertTrue(embeddedTexts.isEmpty())

        manual.body = "Updated searchable text"
        manager.saveEntity(manual)

        val refreshedRepresentation = requireNotNull(manual.vectorRepresentation())
        assertTrue(refreshedRepresentation.hasHnswVector)
        assertEquals(CALIBRATION, refreshedRepresentation.hnswCalibrationId)
        assertTrue(embeddedTexts.single().contains("Updated searchable text"))
    }

    @Test
    fun `automatic embedding date text is stable across default timezones`() {
        val entity = StableDateSearchEntity().apply {
            occurredAt = Date(0L)
        }
        val descriptor = manager.context.getDescriptorForEntity(entity)
        val previousTimezone = TimeZone.getDefault()

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
            val mountainText = VectorEntityEncoder.searchableText(entity, descriptor)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val tokyoText = VectorEntityEncoder.searchableText(entity, descriptor)

            assertEquals("1970-01-01T00:00:00.000Z", mountainText)
            assertEquals(mountainText, tokyoText)
        } finally {
            TimeZone.setDefault(previousTimezone)
        }
    }

    private fun save(
        title: String,
        body: String,
        category: String,
        note: String,
    ): HighLevelSearchEntity = manager.saveEntity(
        HighLevelSearchEntity().apply {
            this.title = title
            this.body = body
            this.category = category
            this.note = note
        },
    )

    private fun savePartitioned(
        region: String,
        body: String,
        note: String,
    ): PartitionedHighLevelSearchEntity = manager.saveEntity(
        PartitionedHighLevelSearchEntity().apply {
            this.region = region
            this.body = body
            this.note = note
        },
    )

    private fun embeddingFor(text: String): FloatArray = when {
        text == "expense for each animal" -> floatArrayOf(1f, 0f, 0f)
        text.contains("cost per horse", ignoreCase = true) -> floatArrayOf(1f, 0f, 0f)
        else -> floatArrayOf(0f, 1f, 0f)
    }

    private fun assertUnsupportedSearch(mode: SearchMode, block: () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException>(block = block)
        assertTrue(
            error.message.orEmpty().contains(mode.name, ignoreCase = true),
            "Unsupported-search error did not identify $mode: ${error.message}",
        )
    }

    private fun lexicalFeature(entityType: Class<*>, suffix: String): FeatureFingerprint {
        val configuration = VectorManagedConfiguration.forClass(entityType)
        val namespace = "onyx-vector/1/seed:" +
            java.lang.Long.toUnsignedString(VECTOR_FEATURE_SEED, 16) +
            "/${entityType.name}"
        return VectorFeatureHasher.fingerprint("$namespace/$suffix", configuration.entropy)
    }

    private fun VectorRepresentation.containsFeature(feature: FeatureFingerprint): Boolean {
        if (featureWordCount != feature.wordCount) return false
        val storedWords = featureWords
        var offset = 0
        while (offset < storedWords.size) {
            if ((0 until feature.wordCount).all { word -> storedWords[offset + word] == feature[word] }) {
                return true
            }
            offset += feature.wordCount
        }
        return false
    }

    companion object {
        const val CALIBRATION = 0x534541524348L
        const val MANUAL_CALIBRATION = 0x4d414e55414cL
        const val CALLBACK_PENDING = "callback-pending"
        const val CALLBACK_FINAL = "The cost per horse is finalized by a callback."
        const val VECTOR_FEATURE_SEED = 7_640_891_576_956_012_809L
    }
}

@Entity(
    fileName = "lexical-high-level-search/",
    entropy = 64,
    searchSupport = SearchSupport.LEXICAL,
)
private class LexicalHighLevelSearchEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var body: String = ""
}

@Entity(
    fileName = "semantic-high-level-search/",
    entropy = 64,
    searchSupport = SearchSupport.SEMANTIC,
)
private class SemanticHighLevelSearchEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var body: String = ""
}

@Entity(fileName = "manual-hnsw/", entropy = 64)
private class ManualHnswEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute(nullable = false)
    @VectorAttribute(mode = VectorAttributeMode.AUTO)
    var category: String = ""
}

@Entity(fileName = "high-level-search/", entropy = 64)
private class HighLevelSearchEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var title: String = ""

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var body: String = ""

    @Attribute(nullable = false)
    @VectorAttribute(mode = VectorAttributeMode.AUTO)
    var category: String = ""

    @Attribute(nullable = false)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var note: String = ""

    @PreInsert
    fun finalizeSearchableText() {
        if (body == HighLevelSearchIntegrationTest.CALLBACK_PENDING) {
            body = HighLevelSearchIntegrationTest.CALLBACK_FINAL
        }
    }
}

@Entity(fileName = "partitioned-high-level-search/", entropy = 64)
private class PartitionedHighLevelSearchEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Partition
    @Attribute(nullable = false)
    @VectorAttribute(mode = VectorAttributeMode.AUTO)
    var region: String = ""

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var body: String = ""

    @Attribute(nullable = false)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var note: String = ""
}

@Entity(fileName = "unsupported-high-level-search/", entropy = 64)
private class UnsupportedHighLevelSearchEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var body: String = ""
}

@Entity(fileName = "stable-date-search/", entropy = 64)
private class StableDateSearchEntity : VectorManagedEntity() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var id: Long = 0L

    @Attribute(nullable = false)
    @VectorAttribute(
        mode = VectorAttributeMode.SELECTED,
        families = [VectorFeatureFamily.TEXT_TERM],
    )
    var occurredAt: Date = Date(0L)
}
