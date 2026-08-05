package database.index

import com.onyx.extension.referenceId
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeForFetchSequenceGen
import entities.index.StringIdentifierEntityIndex
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BTreeDuplicateIndexRegressionTest {

    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-btree-duplicate-index-")
        factory = newFactory()
        factory.initialize()
        manager = factory.persistenceManager
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
    fun duplicateValuesAcrossLeafSplitsHonorEveryRangeBoundary() {
        val seed = seedSplitIndex()
        val index = indexInteractor()
        val lower = references(seed.lower)
        val equal = references(seed.equal)
        val upper = references(seed.upper)

        assertEquals(equal, index.findAll(EQUAL_VALUE).keys.toSet(), "Equality must return the complete duplicate run")

        assertEquals(upper, index.findAllAbove(EQUAL_VALUE, false), "GT must exclude the complete boundary group")
        assertEquals(equal + upper, index.findAllAbove(EQUAL_VALUE, true), "GTE must include the complete boundary group")
        assertEquals(lower, index.findAllBelow(EQUAL_VALUE, false), "LT must exclude the complete boundary group")
        assertEquals(lower + equal, index.findAllBelow(EQUAL_VALUE, true), "LTE must include the complete boundary group")

        assertEquals(
            equal,
            index.findAllBetween(LOWER_VALUE, false, UPPER_VALUE, false),
            "Exclusive BETWEEN must exclude both endpoint groups"
        )
        assertEquals(
            lower + equal,
            index.findAllBetween(LOWER_VALUE, true, UPPER_VALUE, false),
            "BETWEEN must include every record at an inclusive lower endpoint"
        )
        assertEquals(
            equal + upper,
            index.findAllBetween(LOWER_VALUE, false, UPPER_VALUE, true),
            "BETWEEN must include every record at an inclusive upper endpoint"
        )
        assertEquals(
            lower + equal + upper,
            index.findAllBetween(LOWER_VALUE, true, UPPER_VALUE, true),
            "Inclusive BETWEEN must include all endpoint groups"
        )
        assertEquals(
            equal + upper,
            index.findAllBetween(EQUAL_VALUE, true, UPPER_VALUE, true),
            "An inclusive lower bound must include the complete split duplicate group"
        )
        assertEquals(
            upper,
            index.findAllBetween(EQUAL_VALUE, false, UPPER_VALUE, true),
            "An exclusive lower bound must exclude the complete split duplicate group"
        )
        assertEquals(
            lower + equal,
            index.findAllBetween(LOWER_VALUE, true, EQUAL_VALUE, true),
            "An inclusive upper bound must include the complete split duplicate group"
        )
        assertEquals(
            lower,
            index.findAllBetween(LOWER_VALUE, true, EQUAL_VALUE, false),
            "An exclusive upper bound must exclude the complete split duplicate group"
        )
        assertEquals(
            equal,
            index.findAllBetween(EQUAL_VALUE, true, EQUAL_VALUE, true),
            "A single-value inclusive range must return every duplicate"
        )
        assertTrue(
            index.findAllBetween(EQUAL_VALUE, false, EQUAL_VALUE, true).isEmpty(),
            "A single-value range with an exclusive endpoint must be empty"
        )

        assertEquals(equal, queryReferences(QueryCriteriaOperator.EQUAL, EQUAL_VALUE))
        assertEquals(upper, queryReferences(QueryCriteriaOperator.GREATER_THAN, EQUAL_VALUE))
        assertEquals(equal + upper, queryReferences(QueryCriteriaOperator.GREATER_THAN_EQUAL, EQUAL_VALUE))
        assertEquals(lower, queryReferences(QueryCriteriaOperator.LESS_THAN, EQUAL_VALUE))
        assertEquals(lower + equal, queryReferences(QueryCriteriaOperator.LESS_THAN_EQUAL, EQUAL_VALUE))
        assertEquals(
            lower + equal + upper,
            queryReferences(QueryCriteriaOperator.BETWEEN, Pair(LOWER_VALUE, UPPER_VALUE))
        )
        assertEquals(
            equal,
            queryReferences(QueryCriteriaOperator.BETWEEN, Pair(EQUAL_VALUE, EQUAL_VALUE))
        )
    }

    @Test
    fun updatingAndDeletingOneDuplicatePreservesEverySibling() {
        val seed = seedSplitIndex()
        val originalEqualReferences = references(seed.equal)
        val lowerReferences = references(seed.lower)
        val upperReferences = references(seed.upper)
        val moved = seed.equal[0]
        val deleted = seed.equal[1]
        val movedReference = moved.referenceId(factory.schemaContext)
        val deletedReference = deleted.referenceId(factory.schemaContext)

        moved.indexVal = MOVED_VALUE
        manager.saveEntity(moved)
        assertTrue(manager.deleteEntity(deleted), "The selected duplicate should be deleted")

        val index = indexInteractor()
        val survivingEqualReferences = originalEqualReferences - movedReference - deletedReference

        assertEquals(survivingEqualReferences, index.findAll(EQUAL_VALUE).keys.toSet())
        assertEquals(setOf(movedReference), index.findAll(MOVED_VALUE).keys.toSet())
        assertEquals(lowerReferences, index.findAll(LOWER_VALUE).keys.toSet())
        assertEquals(upperReferences, index.findAll(UPPER_VALUE).keys.toSet())
        assertEquals(
            survivingEqualReferences + movedReference + upperReferences,
            index.findAllAbove(EQUAL_VALUE, true),
            "Updating and deleting individual duplicates must not discard sibling entries"
        )
    }

    @Test
    fun clearRebuildAndReopenPreserveSplitDuplicateEntriesAndReverseLookups() {
        val seed = seedSplitIndex()
        val allEqualReferences = references(seed.equal)
        val deleted = seed.equal[0]
        val moved = seed.equal[1]
        val deletedReference = deleted.referenceId(factory.schemaContext)
        val movedReference = moved.referenceId(factory.schemaContext)

        var index = indexInteractor()
        index.clear()
        assertTrue(index.findAll(EQUAL_VALUE).isEmpty(), "clear must remove the duplicate index entries")

        index.rebuild()
        assertEquals(allEqualReferences, index.findAll(EQUAL_VALUE).keys.toSet())

        assertTrue(manager.deleteEntity(deleted), "Delete after rebuild must use the rebuilt reverse lookup")
        val afterDelete = allEqualReferences - deletedReference
        assertEquals(afterDelete, index.findAll(EQUAL_VALUE).keys.toSet())

        reopen()
        index = indexInteractor()
        assertEquals(afterDelete, index.findAll(EQUAL_VALUE).keys.toSet())
        assertEquals(afterDelete, queryReferences(QueryCriteriaOperator.EQUAL, EQUAL_VALUE))
        assertEquals(
            afterDelete + references(seed.upper),
            index.findAllAbove(EQUAL_VALUE, true),
            "Range traversal must still span the duplicate run after reopen"
        )

        moved.indexVal = MOVED_VALUE
        manager.saveEntity(moved)
        assertEquals(afterDelete - movedReference, index.findAll(EQUAL_VALUE).keys.toSet())
        assertEquals(setOf(movedReference), index.findAll(MOVED_VALUE).keys.toSet())
    }

    @Test
    fun objectIndexPreservesSplitRangesMutationsRebuildAndReopen() {
        val seed = seedStringSplitIndex()
        val lowerReferences = stringReferences(seed.lower)
        val equalReferences = stringReferences(seed.equal)
        val upperReferences = stringReferences(seed.upper)
        var index = stringIndexInteractor()

        assertEquals(equalReferences, index.findAll(STRING_EQUAL_VALUE).keys.toSet())
        assertEquals(upperReferences, index.findAllAbove(STRING_EQUAL_VALUE, false))
        assertEquals(equalReferences + upperReferences, index.findAllAbove(STRING_EQUAL_VALUE, true))
        assertEquals(lowerReferences, index.findAllBelow(STRING_EQUAL_VALUE, false))
        assertEquals(lowerReferences + equalReferences, index.findAllBelow(STRING_EQUAL_VALUE, true))
        assertEquals(
            lowerReferences + equalReferences + upperReferences,
            index.findAllBetween(STRING_LOWER_VALUE, true, STRING_UPPER_VALUE, true)
        )
        assertEquals(
            equalReferences,
            index.findAllBetween(STRING_EQUAL_VALUE, true, STRING_EQUAL_VALUE, true)
        )

        val moved = seed.equal[0]
        val deleted = seed.equal[1]
        val movedReference = moved.referenceId(factory.schemaContext)
        val deletedReference = deleted.referenceId(factory.schemaContext)
        moved.indexValue = STRING_MOVED_VALUE
        manager.saveEntity(moved)
        assertTrue(manager.deleteEntity(deleted))

        val survivingEqualReferences = equalReferences - movedReference - deletedReference
        assertEquals(survivingEqualReferences, index.findAll(STRING_EQUAL_VALUE).keys.toSet())
        assertEquals(setOf(movedReference), index.findAll(STRING_MOVED_VALUE).keys.toSet())

        index.clear()
        assertTrue(index.findAll(STRING_EQUAL_VALUE).isEmpty())
        index.rebuild()
        assertEquals(survivingEqualReferences, index.findAll(STRING_EQUAL_VALUE).keys.toSet())
        assertEquals(setOf(movedReference), index.findAll(STRING_MOVED_VALUE).keys.toSet())

        reopen()
        index = stringIndexInteractor()
        assertEquals(survivingEqualReferences, index.findAll(STRING_EQUAL_VALUE).keys.toSet())
        assertEquals(
            survivingEqualReferences + movedReference + upperReferences,
            index.findAllAbove(STRING_EQUAL_VALUE, true)
        )

        val movedAfterReopen = seed.equal[2]
        val deletedAfterReopen = seed.equal[3]
        val movedAfterReopenReference = movedAfterReopen.referenceId(factory.schemaContext)
        val deletedAfterReopenReference = deletedAfterReopen.referenceId(factory.schemaContext)
        movedAfterReopen.indexValue = STRING_MOVED_VALUE
        manager.saveEntity(movedAfterReopen)
        assertTrue(manager.deleteEntity(deletedAfterReopen))

        assertEquals(
            survivingEqualReferences - movedAfterReopenReference - deletedAfterReopenReference,
            index.findAll(STRING_EQUAL_VALUE).keys.toSet()
        )
        assertEquals(
            setOf(movedReference, movedAfterReopenReference),
            index.findAll(STRING_MOVED_VALUE).keys.toSet()
        )
    }

    private fun seedSplitIndex(): Seed {
        val lower = entities(LOWER_COUNT, LOWER_VALUE)
        val equal = entities(DUPLICATE_COUNT, EQUAL_VALUE)
        val upper = entities(UPPER_COUNT, UPPER_VALUE)
        manager.saveEntities(lower + equal + upper)
        assertTrue((lower + equal + upper).all { it.id != null }, "Sequence identifiers should be assigned during save")
        return Seed(lower, equal, upper)
    }

    private fun seedStringSplitIndex(): StringSeed {
        val lower = stringEntities(LOWER_COUNT, STRING_LOWER_VALUE, "lower")
        val equal = stringEntities(DUPLICATE_COUNT, STRING_EQUAL_VALUE, "equal")
        val upper = stringEntities(UPPER_COUNT, STRING_UPPER_VALUE, "upper")
        manager.saveEntities(lower + equal + upper)
        return StringSeed(lower, equal, upper)
    }

    private fun entities(count: Int, indexValue: Int): List<AllAttributeForFetchSequenceGen> =
        List(count) {
            AllAttributeForFetchSequenceGen().apply {
                indexVal = indexValue
            }
        }

    private fun stringEntities(
        count: Int,
        indexValue: String,
        identifierPrefix: String
    ): List<StringIdentifierEntityIndex> =
        List(count) { ordinal ->
            StringIdentifierEntityIndex().apply {
                identifier = "$identifierPrefix-${ordinal.toString().padStart(4, '0')}"
                this.indexValue = indexValue
            }
        }

    private fun references(entities: Collection<AllAttributeForFetchSequenceGen>): Set<Long> =
        entities.map { it.referenceId(factory.schemaContext) }.toSet()

    private fun stringReferences(entities: Collection<StringIdentifierEntityIndex>): Set<Long> =
        entities.map { it.referenceId(factory.schemaContext) }.toSet()

    private fun queryReferences(operator: QueryCriteriaOperator, value: Any): Set<Long> {
        val query = Query(
            AllAttributeForFetchSequenceGen::class.java,
            QueryCriteria("indexVal", operator, value)
        )
        return manager.executeQuery<AllAttributeForFetchSequenceGen>(query)
            .map { it.referenceId(factory.schemaContext) }
            .toSet()
    }

    private fun indexInteractor(): IndexInteractor {
        val descriptor = factory.schemaContext.getDescriptorForEntity(AllAttributeForFetchSequenceGen::class.java, "")
        return factory.schemaContext.getIndexInteractor(descriptor.indexes["indexVal"]!!)
    }

    private fun stringIndexInteractor(): IndexInteractor {
        val descriptor = factory.schemaContext.getDescriptorForEntity(StringIdentifierEntityIndex::class.java, "")
        return factory.schemaContext.getIndexInteractor(descriptor.indexes["indexValue"]!!)
    }

    private fun reopen() {
        factory.close()
        factory = newFactory()
        factory.initialize()
        manager = factory.persistenceManager
    }

    private fun newFactory() = EmbeddedPersistenceManagerFactory(
        databaseDirectory.toString(),
        addShutdownHook = false
    )

    private data class Seed(
        val lower: List<AllAttributeForFetchSequenceGen>,
        val equal: List<AllAttributeForFetchSequenceGen>,
        val upper: List<AllAttributeForFetchSequenceGen>
    )

    private data class StringSeed(
        val lower: List<StringIdentifierEntityIndex>,
        val equal: List<StringIdentifierEntityIndex>,
        val upper: List<StringIdentifierEntityIndex>
    )

    private companion object {
        // Int posting leaves hold 451 keys in v2; 500 equal keys force the duplicate run across a split.
        const val DUPLICATE_COUNT = 500
        const val LOWER_COUNT = 3
        const val UPPER_COUNT = 4
        const val LOWER_VALUE = 10
        const val EQUAL_VALUE = 20
        const val MOVED_VALUE = 25
        const val UPPER_VALUE = 30
        const val STRING_LOWER_VALUE = "alpha"
        const val STRING_EQUAL_VALUE = "middle"
        const val STRING_MOVED_VALUE = "moved"
        const val STRING_UPPER_VALUE = "omega"
    }
}
