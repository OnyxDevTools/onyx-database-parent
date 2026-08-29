package com.onyx.cloud.extensions

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.GZIPOutputStream
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntityMessagePackTest {
    @Test
    fun canonicalGoldenFixtureMatchesEverySdk() {
        val fixture = canonicalFixture()
        val expectedHex =
            "82a6656e7469747986a26964f9a46e616d65ac4dc3b8c3b8736520f09f9a80" +
                "a6616374697665c3a573636f7265cb4029000000000000a86e756c6c61626c65" +
                "c0a47461677392a5616c706861a2ceb2a47061676502"

        val encoded = EntityMessagePack.encode(fixture)

        assertEquals(expectedHex, encoded.toHex())
        assertEquals(fixture, EntityMessagePack.decode(encoded))
    }

    @Test
    fun recursivelySerializesObjectsDatesAndCycles() {
        val root = Node("root", Date(0))
        root.child = root

        val decoded = assertIs<Map<*, *>>(EntityMessagePack.decode(EntityMessagePack.encode(root)))

        assertEquals("root", decoded["name"])
        assertEquals("1970-01-01T00:00:00Z", decoded["created"])
        assertEquals(mapOf("cyclicReference" to "detected"), decoded["child"])
    }

    @Test
    fun rejectsValuesOutsidePortableV1Profile() {
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.decode(byteArrayOf(0xc4.toByte(), 0x01, 0x00))
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.decode(byteArrayOf(0xd4.toByte(), 0x01, 0x00))
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.decode(byteArrayOf(0x81.toByte(), 0x01, 0xc0.toByte()))
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.decode(byteArrayOf(0xd9.toByte(), 0x01, 0xff.toByte()))
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.decode(
                byteArrayOf(
                    0xcf.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                    0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.decode(EntityMessagePack.encode(true) + EntityMessagePack.encode(false))
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.encode(mapOf(1 to "non-string key"))
        }
        assertFailsWith<IllegalArgumentException> {
            EntityMessagePack.encode(Double.NaN)
        }
    }

    @Test
    fun enforcesDepthLimit() {
        var value: Any? = null
        repeat(EntityMessagePack.MAX_DEPTH + 1) { value = listOf(value) }

        assertFailsWith<IllegalArgumentException> { EntityMessagePack.encode(value) }
    }

    @Test
    fun decodesConcatenatedStreamValuesIncludingInitialNil() {
        val wire = EntityMessagePack.encode(null) +
            EntityMessagePack.encode(mapOf("action" to "CREATE")) +
            EntityMessagePack.encode(mapOf("action" to "UPDATE"))
        val values = mutableListOf<Any?>()

        EntityMessagePack.decodeSequence(ByteArrayInputStream(wire), values::add)

        assertEquals(null, values[0])
        assertEquals("CREATE", (values[1] as Map<*, *>)["action"])
        assertEquals("UPDATE", (values[2] as Map<*, *>)["action"])
    }

    @Test
    fun representativeEntityPageIsSmallerAndReportsCodecTiming() {
        val page = linkedMapOf(
            "records" to List(500) { index ->
                linkedMapOf(
                    "id" to index.toLong(),
                    "name" to "entity-$index",
                    "active" to (index % 2 == 0),
                    "score" to index / 10.0,
                    "nullable" to null,
                    "tags" to listOf("alpha", "β", "group-${index % 8}"),
                )
            },
            "nextPage" to "page-2",
            "totalResults" to 4_000L,
        )
        val jsonText = gson.toJson(page)
        val jsonBytes = jsonText.toByteArray()
        val messagePackBytes = EntityMessagePack.encode(page)
        repeat(50) {
            gson.toJson(page)
            gson.fromJson(jsonText, Any::class.java)
            EntityMessagePack.encode(page)
            EntityMessagePack.decode(messagePackBytes)
        }

        val iterations = 200
        val jsonEncodeNanos = measureNanoTime { repeat(iterations) { gson.toJson(page) } }
        val messagePackEncodeNanos = measureNanoTime { repeat(iterations) { EntityMessagePack.encode(page) } }
        val jsonDecodeNanos = measureNanoTime { repeat(iterations) { gson.fromJson(jsonText, Any::class.java) } }
        val messagePackDecodeNanos = measureNanoTime { repeat(iterations) { EntityMessagePack.decode(messagePackBytes) } }
        val gzipJson = jsonBytes.gzip().size
        val gzipMessagePack = messagePackBytes.gzip().size

        println(
            "Kotlin entity codec benchmark: json=${jsonBytes.size}B, msgpack=${messagePackBytes.size}B, " +
                "gzipJson=${gzipJson}B, gzipMsgpack=${gzipMessagePack}B, " +
                "jsonEncode=${jsonEncodeNanos / iterations}ns/op, " +
                "msgpackEncode=${messagePackEncodeNanos / iterations}ns/op, " +
                "jsonDecode=${jsonDecodeNanos / iterations}ns/op, " +
                "msgpackDecode=${messagePackDecodeNanos / iterations}ns/op",
        )
        assertTrue(messagePackBytes.size < jsonBytes.size)
    }

    private data class Node(
        val name: String,
        val created: Date,
        var child: Node? = null,
    )

    private fun canonicalFixture(): LinkedHashMap<String, Any?> = linkedMapOf(
        "entity" to linkedMapOf(
            "id" to -7L,
            "name" to "Møøse 🚀",
            "active" to true,
            "score" to 12.5,
            "nullable" to null,
            "tags" to listOf("alpha", "β"),
        ),
        "page" to 2L,
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun ByteArray.gzip(): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(this) }
        output.toByteArray()
    }
}
