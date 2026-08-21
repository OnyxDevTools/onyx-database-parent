package diskmap

import com.onyx.diskmap.data.IndexPostingPage
import com.onyx.diskmap.data.IndexPostingPage.ValueKind
import com.onyx.diskmap.store.impl.InMemoryStore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexPostingPageFormatTest {

    @Test
    fun roundTripsEveryNativeValueWidthAndSignedness() {
        val store = InMemoryStore(null, "posting-page-widths-${System.nanoTime()}")
        try {
            val specifications = listOf(
                TokenSpecification("byte", ValueKind.INTEGRAL, 1, true, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()),
                TokenSpecification("boolean", ValueKind.INTEGRAL, 1, false, 0L, 1L),
                TokenSpecification("short", ValueKind.INTEGRAL, 2, true, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()),
                TokenSpecification("char", ValueKind.INTEGRAL, 2, false, Char.MIN_VALUE.code.toLong(), Char.MAX_VALUE.code.toLong()),
                TokenSpecification("int", ValueKind.INTEGRAL, 4, true, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()),
                TokenSpecification(
                    "float",
                    ValueKind.FLOAT,
                    4,
                    false,
                    java.lang.Float.floatToIntBits(Float.NEGATIVE_INFINITY).toLong(),
                    java.lang.Float.floatToIntBits(Float.NaN).toLong()
                ),
                TokenSpecification("long", ValueKind.INTEGRAL, 8, true, Long.MIN_VALUE, Long.MAX_VALUE),
                TokenSpecification(
                    "double",
                    ValueKind.DOUBLE,
                    8,
                    false,
                    java.lang.Double.doubleToLongBits(Double.NEGATIVE_INFINITY),
                    java.lang.Double.doubleToLongBits(Double.NaN)
                ),
                TokenSpecification("date", ValueKind.DATE, 8, true, Long.MIN_VALUE, Long.MAX_VALUE),
                TokenSpecification("object", ValueKind.OBJECT, 8, false, 17L, MAX_BIG_INT)
            )

            specifications.forEachIndexed { ordinal, specification ->
                val page = IndexPostingPage.create(
                    store,
                    leaf = true,
                    valueKind = specification.valueKind,
                    valueTokenWidth = specification.width,
                    signedValueToken = specification.signed
                )
                assertEquals(
                    (IndexPostingPage.PAGE_SIZE - PAGE_HEADER_SIZE) / (specification.width + BIG_INT_SIZE),
                    page.capacity,
                    specification.name
                )
                assertTrue(page.children.isEmpty(), "${specification.name} leaves must not allocate child arrays")
                if (specification.valueKind == ValueKind.OBJECT) {
                    assertEquals(page.capacity + 1, page.decodedValues.size, specification.name)
                } else {
                    assertTrue(page.decodedValues.isEmpty(), "${specification.name} must not allocate decoded values")
                }
                page.keyCount = 2
                page.valueTokens[0] = specification.firstToken
                page.valueTokens[1] = specification.secondToken
                page.recordIds[0] = 1L + ordinal
                page.recordIds[1] = MAX_BIG_INT - ordinal
                page.previousLeaf = MAX_BIG_INT - 2L * ordinal
                page.nextLeaf = MAX_BIG_INT - 2L * ordinal - 1L
                page.setDecodedValue(0, if (specification.valueKind == ValueKind.OBJECT) "cached" else null)
                page.write(store)

                val reopened = IndexPostingPage.get(store, page.position, specification.valueKind)
                assertEquals(specification.width, reopened.valueTokenWidth, specification.name)
                assertEquals(page.capacity, reopened.capacity, specification.name)
                assertEquals(specification.persisted(specification.firstToken), reopened.valueTokens[0], specification.name)
                assertEquals(specification.persisted(specification.secondToken), reopened.valueTokens[1], specification.name)
                assertEquals(page.recordIds.take(2), reopened.recordIds.take(2), specification.name)
                assertEquals(page.previousLeaf, reopened.previousLeaf, specification.name)
                assertEquals(page.nextLeaf, reopened.nextLeaf, specification.name)
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun usesFiveByteInternalChildrenAndDynamicCompactCapacity() {
        val store = InMemoryStore(null, "posting-page-pointers-${System.nanoTime()}")
        try {
            val internal = IndexPostingPage.create(
                store,
                leaf = false,
                valueKind = ValueKind.INTEGRAL,
                valueTokenWidth = Int.SIZE_BYTES,
                signedValueToken = true
            )
            assertEquals(
                (IndexPostingPage.PAGE_SIZE - PAGE_HEADER_SIZE) / (Int.SIZE_BYTES + 2 * BIG_INT_SIZE),
                internal.capacity
            )
            internal.keyCount = 2
            internal.children[0] = MAX_BIG_INT
            internal.valueTokens[0] = Int.MIN_VALUE.toLong()
            internal.recordIds[0] = MAX_BIG_INT - 1
            internal.children[1] = MAX_BIG_INT - 2
            internal.valueTokens[1] = Int.MAX_VALUE.toLong()
            internal.recordIds[1] = MAX_BIG_INT - 3
            internal.children[2] = MAX_BIG_INT - 4
            internal.write(store)

            val reopened = IndexPostingPage.get(
                store,
                internal.position,
                ValueKind.INTEGRAL,
                Int.SIZE_BYTES,
                true
            )
            assertEquals(internal.children.take(3), reopened.children.take(3))

            listOf(1, 2, 4, 8).forEach { width ->
                val compact = IndexPostingPage.create(
                    store,
                    leaf = true,
                    compact = true,
                    valueKind = ValueKind.INTEGRAL,
                    valueTokenWidth = width,
                    signedValueToken = true
                )
                assertEquals(
                    (IndexPostingPage.COMPACT_PAGE_SIZE - PAGE_HEADER_SIZE) / (width + BIG_INT_SIZE),
                    compact.capacity
                )
            }
        } finally {
            store.close()
        }
    }

    private data class TokenSpecification(
        val name: String,
        val valueKind: ValueKind,
        val width: Int,
        val signed: Boolean,
        val firstToken: Long,
        val secondToken: Long
    ) {
        fun persisted(token: Long): Long = when {
            signed || width == Long.SIZE_BYTES -> token
            else -> token and ((1L shl (width * Byte.SIZE_BITS)) - 1L)
        }
    }

    private companion object {
        const val PAGE_HEADER_SIZE = 32
        const val BIG_INT_SIZE = 5
        const val MAX_BIG_INT = (1L shl 40) - 1L
    }
}
