package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.diskmap.DiskMap
import com.onyx.entity.SystemEntity
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.SearchVectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.PostPersist
import com.onyx.persistence.annotations.PrePersist
import com.onyx.persistence.annotations.SearchSupport
import com.onyx.persistence.annotations.SearchVector
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.findById
import com.onyx.persistence.query.from
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.hnswCandidates
import com.onyx.vector.SearchEmbeddingProvider
import com.onyx.vector.SearchVectorConfiguration
import com.onyx.vector.VectorManagedConfiguration
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ResolverSearchVectorIntegrationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-resolver-vector-")
        open()
    }

    private fun open() {
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = directory.toString(), instance = directory.toString(), addShutdownHook = false,
        ).apply {
            storeType = StoreType.FILE
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
        manager.searchEmbeddingProvider = SearchEmbeddingProvider { _, _ ->
            error("Numeric resolver must take precedence over automatic text embeddings")
        }
        NumericResolverEntity.postCalls = 0
        NumericResolverEntity.getterCalls = 0
    }

    @After
    fun cleanup() {
        try { factory.close() } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun `removing getter configuration clears only its generated HNSW bytes and permits manual replacements`() {
        val config = SearchVectorConfiguration(2, "removed-getter")
        val generated = manager.saveEntity(LegacyNamedVectorEntity().apply {
            id = "generated"
            searchVector = "keep stored attributes"
            hnswVector(floatArrayOf(1f, 0.5f), config.calibrationId)
        })
        manager.saveEntity(LegacyNamedVectorEntity().apply {
            id = "manual"
            hnswVector(floatArrayOf(1f, 0.5f), config.calibrationId)
        })
        val descriptor = manager.context.getBaseDescriptorForEntity(LegacyNamedVectorEntity::class.java)!!
        val current = VectorManagedConfiguration.forClass(LegacyNamedVectorEntity::class.java)
        val priorSignature = current.signature + "|onyx-search-vector-v1|COSINE|2|removed-getter|hnswQuantization:2"
        val priorId = ByteBuffer.wrap(
            MessageDigest.getInstance("SHA-256").digest(priorSignature.toByteArray(Charsets.UTF_8))
        ).long.let { if (it == 0L) 1L else it }
        // Persist the prior schema and its actual max-absolute row payload under this same class
        // name. Reopening must migrate it using today's unconfigured class definition.
        generated.vectorRepresentation(generated.vectorRepresentation()!!.copy(
            configurationId = priorId,
            hnswVector = byteArrayOf(127, 64),
        ))
        val records: DiskMap<Any, IManagedEntity> = manager.context.getDataFile(descriptor)
            .getHashMap(descriptor.identifier!!.type, descriptor.entityClass.name)
        records.putAndGet(generated.id, generated)
        val system = manager.context.serializedPersistenceManager
        val priorSchema = system.from<SystemEntity>()
            .where("name" eq LegacyNamedVectorEntity::class.java.canonicalName)
            .and("isLatestVersion" eq true)
            .first<SystemEntity>()
        priorSchema.indexes.first { it.name == VectorManagedEntity.REPRESENTATION_FIELD }.apply {
            configurationId = priorId
            configurationSignature = priorSignature
        }
        system.saveEntity(priorSchema)
        factory.close()
        open()

        val migrated = manager.findById<LegacyNamedVectorEntity>("generated")!!
        assertEquals("keep stored attributes", migrated.searchVector)
        assertFalse(migrated.vectorRepresentation()!!.hasHnswVector)
        assertContentEquals(byteArrayOf(114, 57),
            manager.findById<LegacyNamedVectorEntity>("manual")!!.vectorRepresentation()!!.hnswVector)
        fun matches() = manager.from<LegacyNamedVectorEntity>()
            .hnswCandidates(config.query(floatArrayOf(1f, 0.5f), 10, 20, minScore = 0.999999f))
            .list<LegacyNamedVectorEntity>().map { it.id }.toSet()
        assertEquals(setOf("manual"), matches())
        migrated.hnswVector(floatArrayOf(1f, 0.5f), config.calibrationId)
        manager.saveEntity(migrated)
        val currentDescriptor = manager.context.getBaseDescriptorForEntity(LegacyNamedVectorEntity::class.java)!!
        manager.context.getIndexInteractor(currentDescriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD]!!)
            .rebuild()
        assertEquals(setOf("manual", "generated"), matches(), "Later manual assignments survive rebuilds")
        factory.close()
        open()
        assertEquals(setOf("manual", "generated"), matches())
    }

    @Test
    fun `legacy getter names and manually selected calibration retain their original behavior`() {
        val config = SearchVectorConfiguration(2, "legacy-manual")
        manager.saveEntity(LegacyNamedVectorEntity().apply {
            id = "legacy"
            searchVector = "ordinary stored text"
            searchVectorConfiguration = "ordinary configuration field"
            hnswVector(floatArrayOf(1f, 0.5f), config.calibrationId)
        })
        fun assertLegacy() {
            val row = manager.from<LegacyNamedVectorEntity>()
                .hnswCandidates(config.query(floatArrayOf(1f, 0.5f), 10, 20, minScore = 0.999999f))
                .list<LegacyNamedVectorEntity>().single()
            assertEquals("ordinary stored text", row.searchVector)
            assertEquals("ordinary configuration field", row.searchVectorConfiguration)
            assertContentEquals(byteArrayOf(114, 57), row.vectorRepresentation()!!.hnswVector)
        }
        assertLegacy()
        factory.close()
        open()
        assertLegacy()
    }

    @Test
    fun `344 scalar pairs preserve magnitude and refresh on update null deletion and reopen`() {
        save("near", 0.2f)
        save("far", 0.9f)
        assertEquals("near", nearest(0.21f).first().id)
        val near = manager.findById<NumericResolverEntity>("near")!!
        near.position = -0.8f
        manager.saveEntity(near)
        assertEquals("far", nearest(0.8f).first().id)

        val far = manager.findById<NumericResolverEntity>("far")!!
        far.ready = false
        manager.saveEntity(far)
        assertEquals(listOf("near"), nearest(0.8f).map { it.id })
        assertFalse(manager.findById<NumericResolverEntity>("far")!!.vectorRepresentation()!!.hasHnswVector)

        factory.close()
        open()
        assertEquals(listOf("near"), nearest(-0.8f).map { it.id })
        assertTrue(manager.deleteEntity(manager.findById<NumericResolverEntity>("near")!!))
        assertTrue(nearest(0.8f).isEmpty())
    }

    @Test
    fun `pre callbacks precede resolver validation and bad vectors do not replace persisted values`() {
        val entity = save("pre", 0f, prePosition = 0.7f)
        assertEquals("pre", nearest(0.7f).first().id)
        val original = manager.findById<NumericResolverEntity>("pre")!!.vectorRepresentation()
        entity.dimensions = 2
        assertFails { manager.saveEntity(entity) }
        assertEquals(original, manager.findById<NumericResolverEntity>("pre")!!.vectorRepresentation())
        entity.dimensions = 688
        entity.prePosition = null
        entity.position = Float.NaN
        assertFails { manager.saveEntity(entity) }
        assertEquals(original, manager.findById<NumericResolverEntity>("pre")!!.vectorRepresentation())
    }

    @Test
    fun `post callbacks do not reevaluate the getter or rewrite the stored vector`() {
        val entity = save("single", 0.2f, postPosition = 0.9f)
        assertEquals(1, NumericResolverEntity.postCalls)
        assertEquals(1, NumericResolverEntity.getterCalls)
        assertEquals(0.9f, entity.position, "Post callbacks still run normally")
        val stored = manager.findById<NumericResolverEntity>("single")!!
        assertEquals(0.2f, stored.position, "Post callback mutations are not implicitly persisted")
        val originalVector = stored.vectorRepresentation()!!.hnswVector
        assertTrue(originalVector.isNotEmpty())
        assertContentEquals(originalVector, entity.vectorRepresentation()!!.hnswVector)

        manager.saveEntity(entity)
        assertEquals(2, NumericResolverEntity.getterCalls, "Each explicit save evaluates the getter once")
        assertEquals(2, NumericResolverEntity.postCalls)
        val updated = manager.findById<NumericResolverEntity>("single")!!
        assertEquals(0.9f, updated.position)
        assertFalse(originalVector.contentEquals(updated.vectorRepresentation()!!.hnswVector))
        factory.close()
        open()
        assertEquals(0.9f, manager.findById<NumericResolverEntity>("single")!!.position)
        assertContentEquals(updated.vectorRepresentation()!!.hnswVector,
            manager.findById<NumericResolverEntity>("single")!!.vectorRepresentation()!!.hnswVector)
        assertEquals(entity.id, nearest(0.9f).first().id)
    }

    @Test
    fun `null getter remains unindexed after post callback until the next explicit save`() {
        val entity = manager.saveEntity(NumericResolverEntity().apply {
            id = "not-ready"
            ready = false
            postReady = true
        })
        assertTrue(entity.ready)
        assertEquals(1, NumericResolverEntity.getterCalls)
        assertEquals(1, NumericResolverEntity.postCalls)
        assertFalse(manager.findById<NumericResolverEntity>(entity.id)!!.ready)
        assertFalse(manager.findById<NumericResolverEntity>(entity.id)!!.vectorRepresentation()!!.hasHnswVector)
        assertTrue(nearest(0f).isEmpty())

        manager.saveEntity(entity)
        assertEquals(2, NumericResolverEntity.getterCalls)
        assertEquals(2, NumericResolverEntity.postCalls)
        assertTrue(manager.findById<NumericResolverEntity>(entity.id)!!.ready)
        assertEquals(listOf(entity.id), nearest(0f).map { it.id })
    }

    @Test
    fun `heterogeneous near-neutral HNSW candidates retain score precision after reopening`() {
        val source = FloatArray(344) { ((it % 29) - 14) * 0.0007f }
        val changed = FloatArray(344) { source[it] + if (it % 3 == 0) 0.008f else -0.006f }
        // Insert the distractor first: the former coarse representation tied both rows and
        // therefore incorrectly admitted it through the tight score threshold.
        manager.saveEntity(PrecisionResolverEntity().apply { id = "distractor"; features = changed })
        val target = manager.saveEntity(PrecisionResolverEntity().apply { id = "target"; features = source })
        assertContentEquals(legacyUnitQuantize(densePairs(source)), legacyUnitQuantize(densePairs(changed)))
        assertTrue(target.vectorRepresentation()!!.hnswVector.any { kotlin.math.abs(it.toInt()) == 127 })
        assertEquals(listOf("target"), precisionNeighbors(source))
        factory.close()
        open()
        assertEquals(listOf("target"), precisionNeighbors(source))
    }

    @Test
    fun `reopening a previous numeric index configuration rebuilds coarse bytes from source getters`() {
        val source = FloatArray(344) { ((it % 29) - 14) * 0.0007f }
        val changed = FloatArray(344) { source[it] + if (it % 3 == 0) 0.008f else -0.006f }
        val rows = listOf(
            manager.saveEntity(PrecisionResolverEntity().apply { id = "distractor"; features = changed }),
            manager.saveEntity(PrecisionResolverEntity().apply { id = "target"; features = source }),
        )
        val descriptor = manager.context.getBaseDescriptorForEntity(PrecisionResolverEntity::class.java)!!
        val configuration = VectorManagedConfiguration.forClass(PrecisionResolverEntity::class.java)
        val oldSignature = configuration.signature.replace("|hnswQuantization:2", "")
        assertNotEquals(configuration.signature, oldSignature)
        val oldConfigurationId = ByteBuffer.wrap(
            MessageDigest.getInstance("SHA-256").digest(oldSignature.toByteArray(Charsets.UTF_8))
        ).long.let { if (it == 0L) 1L else it }
        assertNotEquals(configuration.configurationId, oldConfigurationId)

        // Simulate the previous writer's actual persisted record payload and schema metadata.
        // Clearing the graph ensures that query success requires a real migration rebuild.
        val index = manager.context.getIndexInteractor(descriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD]!!)
        index.clear()
        val records: DiskMap<Any, IManagedEntity> = manager.context.getDataFile(descriptor)
            .getHashMap(descriptor.identifier!!.type, descriptor.entityClass.name)
        rows.forEach { row ->
            row.vectorRepresentation(row.vectorRepresentation()!!.copy(
                configurationId = oldConfigurationId,
                hnswVector = legacyUnitQuantize(densePairs(row.features)),
            ))
            records.putAndGet(row.id, row)
        }
        val system = manager.context.serializedPersistenceManager
        val oldSchema = system.from<SystemEntity>()
            .where("name" eq PrecisionResolverEntity::class.java.canonicalName)
            .and("isLatestVersion" eq true)
            .first<SystemEntity>()
        oldSchema.indexes.first { it.name == VectorManagedEntity.REPRESENTATION_FIELD }.apply {
            configurationId = oldConfigurationId
            configurationSignature = oldSignature
        }
        system.saveEntity(oldSchema)
        val stableCalibrationId = rows.last().searchVectorConfiguration!!.calibrationId
        factory.close()
        PrecisionResolverEntity.getterCalls = 0
        open()

        assertEquals(listOf("target"), precisionNeighbors(source))
        assertEquals(2, PrecisionResolverEntity.getterCalls, "Schema migration must evaluate each stored source")
        val refreshed = manager.findById<PrecisionResolverEntity>("target")!!.vectorRepresentation()!!
        assertEquals(configuration.configurationId, refreshed.configurationId)
        assertEquals(stableCalibrationId, refreshed.hnswCalibrationId)
        assertTrue(refreshed.hnswVector.any { kotlin.math.abs(it.toInt()) == 127 })
        assertFalse(refreshed.hnswVector.contentEquals(legacyUnitQuantize(densePairs(source))))
        factory.close()
        PrecisionResolverEntity.getterCalls = 0
        open()
        assertEquals(listOf("target"), precisionNeighbors(source))
        assertEquals(0, PrecisionResolverEntity.getterCalls, "Ordinary reopen must use the persisted graph")
    }

    private fun precisionNeighbors(source: FloatArray) = manager.from<PrecisionResolverEntity>()
        .hnswCandidates(SearchVectorConfiguration(688, "precision-pairs-v1").query(
            densePairs(source), maxCandidates = 10, efSearch = 20, minScore = 0.99999f,
        ))
        .list<PrecisionResolverEntity>().map { it.id }

    private fun legacyUnitQuantize(vector: FloatArray): ByteArray {
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        return ByteArray(vector.size) { Math.round(vector[it] / norm * 127).toByte() }
    }

    private fun save(id: String, position: Float, prePosition: Float? = null, postPosition: Float? = null) =
        manager.saveEntity(NumericResolverEntity().apply {
            this.id = id
            this.position = position
            this.prePosition = prePosition
            this.postPosition = postPosition
        })

    private fun nearest(position: Float): List<NumericResolverEntity> = manager.from<NumericResolverEntity>()
        .hnswCandidates(SearchVectorConfiguration(688, "bar-pairs-v1").query(pairs(position), 10, 20))
        .list<NumericResolverEntity>()
}

@Entity(searchSupport = SearchSupport.SEMANTIC)
class LegacyNamedVectorEntity : VectorManagedEntity() {
    @Identifier var id: String = ""
    @Attribute var searchVector: String = ""
    @Attribute var searchVectorConfiguration: String = ""
}

private fun pairs(position: Float, dimensions: Int = 688): FloatArray = FloatArray(dimensions) {
    val radians = Math.PI * position / 2.0
    if (it % 2 == 0) cos(radians).toFloat() else sin(radians).toFloat()
}

@Entity(searchSupport = SearchSupport.SEMANTIC)
@SearchVector(dimensions = 688, version = "bar-pairs-v1", resolver = "features")
class NumericResolverEntity : SearchVectorManagedEntity() {
    @Identifier var id: String = ""
    @Attribute var position: Float = 0f
    @Attribute var prePosition: Float? = null
    @Attribute var ready: Boolean = true
    @Attribute var dimensions: Int = 688
    @Attribute var postPosition: Float? = null
    @Attribute var postReady: Boolean? = null
    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.SELECTED, families = [VectorFeatureFamily.TEXT_TERM])
    var title: String = "Automatic text provider must not run"

    override val searchVector: FloatArray?
        get() {
            getterCalls++
            if (!ready) return null
            return pairs(position, dimensions)
        }

    @PrePersist
    fun prepare() { prePosition?.let { position = it } }

    @PostPersist
    fun afterPersist() {
        postCalls++
        postPosition?.let { position = it }
        postReady?.let { ready = it }
    }

    companion object {
        var postCalls: Int = 0
        var getterCalls: Int = 0
    }
}

private fun densePairs(values: FloatArray) = FloatArray(values.size * 2) {
    val angle = Math.PI * values[it / 2] / 2.0
    (if (it % 2 == 0) cos(angle) else sin(angle)).toFloat()
}

@Entity(searchSupport = SearchSupport.SEMANTIC)
@SearchVector(dimensions = 688, version = "precision-pairs-v1")
class PrecisionResolverEntity : SearchVectorManagedEntity() {
    @Identifier var id: String = ""
    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.IGNORE)
    var features: FloatArray = FloatArray(344)

    override val searchVector: FloatArray
        get() {
            getterCalls++
            return densePairs(features)
        }

    companion object {
        var getterCalls = 0
    }
}
