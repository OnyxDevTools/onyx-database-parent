package com.onyx.cloud.extensions

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageIntegerOverflowException
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.ValueType
import java.io.InputStream
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/** Registered media type used by the schema-free entity wire protocol. */
const val ENTITY_MESSAGE_PACK_MEDIA_TYPE: String = "application/vnd.msgpack"

/**
 * Strict MessagePack v1 codec shared by entity requests, responses, and live-query streams.
 *
 * The supported value graph deliberately mirrors portable JSON: null, booleans, signed 64-bit
 * integers, finite floating-point numbers, UTF-8 strings, arrays, and string-keyed maps. MessagePack
 * binary/extension values and non-string map keys are rejected so every supported SDK observes the
 * same recursive data model.
 */
internal object EntityMessagePack {
    const val MAX_BODY_BYTES: Int = 64 * 1024 * 1024
    const val MAX_DEPTH: Int = 128
    const val MAX_CONTAINER_ITEMS: Int = 1_000_000
    const val MAX_STRING_BYTES: Int = 16 * 1024 * 1024
    const val MAX_NODES: Int = 2_000_000

    private val fieldsByClass = ConcurrentHashMap<Class<*>, List<Field>>()

    fun encode(value: Any?): ByteArray {
        val packer = MessagePack.newDefaultBufferPacker()
        try {
            val state = EncodeState()
            packValue(packer, value, 0, state)
            packer.flush()
            val bytes = packer.toByteArray()
            require(bytes.size <= MAX_BODY_BYTES) {
                "MessagePack body exceeds the $MAX_BODY_BYTES-byte limit"
            }
            return bytes
        } finally {
            packer.close()
        }
    }

    fun decode(bytes: ByteArray): Any? {
        require(bytes.size <= MAX_BODY_BYTES) {
            "MessagePack body exceeds the $MAX_BODY_BYTES-byte limit"
        }
        MessagePack.newDefaultUnpacker(bytes).use { unpacker ->
            require(unpacker.hasNext()) { "MessagePack body is empty" }
            val value = unpackValue(unpacker, 0, DecodeState())
            require(!unpacker.hasNext()) { "MessagePack body contains trailing values" }
            return value
        }
    }

    fun decodeToJson(bytes: ByteArray): String = gson.toJson(decode(bytes))

    /** Reads concatenated, self-delimiting MessagePack values until the stream ends. */
    fun decodeSequence(input: InputStream, consume: (Any?) -> Unit) {
        MessagePack.newDefaultUnpacker(input).use { unpacker ->
            while (unpacker.hasNext()) {
                val frameStart = unpacker.totalReadBytes
                val value = unpackValue(unpacker, 0, DecodeState())
                require(unpacker.totalReadBytes - frameStart <= MAX_BODY_BYTES) {
                    "MessagePack stream value exceeds the $MAX_BODY_BYTES-byte limit"
                }
                consume(value)
            }
        }
    }

    private fun packValue(
        packer: org.msgpack.core.MessagePacker,
        value: Any?,
        depth: Int,
        state: EncodeState,
    ) {
        state.visit(depth)
        when (value) {
            null, JsonNull.INSTANCE -> packer.packNil()
            is Boolean -> packer.packBoolean(value)
            is Byte -> packer.packLong(value.toLong())
            is Short -> packer.packLong(value.toLong())
            is Int -> packer.packLong(value.toLong())
            is Long -> packer.packLong(value)
            is BigInteger -> packer.packLong(value.longValueExact())
            is Float -> {
                require(value.isFinite()) { "MessagePack floating-point values must be finite" }
                packer.packFloat(value)
            }
            is Double -> {
                require(value.isFinite()) { "MessagePack floating-point values must be finite" }
                packer.packDouble(value)
            }
            is BigDecimal -> {
                val number = value.toDouble()
                require(number.isFinite()) { "MessagePack floating-point value is outside the finite range" }
                packer.packDouble(number)
            }
            is Number -> {
                val number = value.toDouble()
                require(number.isFinite()) { "MessagePack floating-point values must be finite" }
                packer.packDouble(number)
            }
            is CharSequence -> packString(packer, value.toString())
            is Char -> packString(packer, value.toString())
            is Enum<*> -> packString(packer, value.name)
            is Date -> packString(packer, value.toInstant().toString())
            is Instant -> packString(packer, value.toString())
            is TemporalAccessor -> packString(packer, value.toString())
            is JsonElement -> packJsonElement(packer, value, depth, state)
            is Map<*, *> -> withActive(value, packer, depth, state) {
                require(value.size <= MAX_CONTAINER_ITEMS) {
                    "MessagePack map exceeds the $MAX_CONTAINER_ITEMS-entry limit"
                }
                packer.packMapHeader(value.size)
                value.forEach { (key, child) ->
                    require(key is String) { "MessagePack map keys must be strings" }
                    state.visit(depth + 1)
                    packString(packer, key)
                    packValue(packer, child, depth + 1, state)
                }
            }
            is Iterable<*> -> withActive(value, packer, depth, state) {
                val children = ArrayList<Any?>()
                val iterator = value.iterator()
                while (iterator.hasNext()) {
                    require(children.size < MAX_CONTAINER_ITEMS) {
                        "MessagePack array exceeds the $MAX_CONTAINER_ITEMS-item limit"
                    }
                    children.add(iterator.next())
                }
                packer.packArrayHeader(children.size)
                children.forEach { packValue(packer, it, depth + 1, state) }
            }
            else -> when {
                value.javaClass.isArray -> withActive(value, packer, depth, state) {
                    val size = ReflectArray.getLength(value)
                    require(size <= MAX_CONTAINER_ITEMS) {
                        "MessagePack array exceeds the $MAX_CONTAINER_ITEMS-item limit"
                    }
                    packer.packArrayHeader(size)
                    repeat(size) { packValue(packer, ReflectArray.get(value, it), depth + 1, state) }
                }
                else -> packObject(packer, value, depth, state)
            }
        }
    }

    private fun packJsonElement(
        packer: org.msgpack.core.MessagePacker,
        value: JsonElement,
        depth: Int,
        state: EncodeState,
    ) {
        when {
            value.isJsonNull -> packer.packNil()
            value.isJsonObject -> {
                val obj: JsonObject = value.asJsonObject
                require(obj.size() <= MAX_CONTAINER_ITEMS) {
                    "MessagePack map exceeds the $MAX_CONTAINER_ITEMS-entry limit"
                }
                packer.packMapHeader(obj.size())
                obj.entrySet().forEach { (key, child) ->
                    state.visit(depth + 1)
                    packString(packer, key)
                    packValue(packer, child, depth + 1, state)
                }
            }
            value.isJsonArray -> {
                val array: JsonArray = value.asJsonArray
                require(array.size() <= MAX_CONTAINER_ITEMS) {
                    "MessagePack array exceeds the $MAX_CONTAINER_ITEMS-item limit"
                }
                packer.packArrayHeader(array.size())
                array.forEach { packValue(packer, it, depth + 1, state) }
            }
            else -> {
                val primitive: JsonPrimitive = value.asJsonPrimitive
                when {
                    primitive.isBoolean -> packer.packBoolean(primitive.asBoolean)
                    primitive.isString -> packString(packer, primitive.asString)
                    else -> packJsonNumber(packer, primitive.asString)
                }
            }
        }
    }

    private fun packJsonNumber(packer: org.msgpack.core.MessagePacker, text: String) {
        if (!text.contains('.') && !text.contains('e', ignoreCase = true)) {
            try {
                packer.packLong(BigInteger(text).longValueExact())
                return
            } catch (_: NumberFormatException) {
                throw IllegalArgumentException("Invalid JSON number: $text")
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("JSON integer exceeds the signed 64-bit MessagePack profile")
            }
        }
        val number = text.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid JSON number: $text")
        require(number.isFinite()) { "MessagePack floating-point values must be finite" }
        packer.packDouble(number)
    }

    private fun packObject(
        packer: org.msgpack.core.MessagePacker,
        value: Any,
        depth: Int,
        state: EncodeState,
    ) = withActive(value, packer, depth, state) {
        val fields = serializableFields(value.javaClass)
        require(fields.size <= MAX_CONTAINER_ITEMS) {
            "MessagePack object exceeds the $MAX_CONTAINER_ITEMS-field limit"
        }
        packer.packMapHeader(fields.size)
        fields.forEach { field ->
            val name = field.getAnnotation(SerializedName::class.java)?.value ?: field.name
            state.visit(depth + 1)
            packString(packer, name)
            packValue(packer, field.get(value), depth + 1, state)
        }
    }

    private inline fun withActive(
        value: Any,
        packer: org.msgpack.core.MessagePacker,
        depth: Int,
        state: EncodeState,
        block: () -> Unit,
    ) {
        if (state.active.put(value, true) != null) {
            packer.packMapHeader(1)
            state.visit(depth + 1)
            packString(packer, "cyclicReference")
            packString(packer, "detected")
            return
        }
        try {
            block()
        } finally {
            state.active.remove(value)
        }
    }

    private fun serializableFields(type: Class<*>): List<Field> = fieldsByClass.computeIfAbsent(type) {
        buildList {
            var current: Class<*>? = it
            while (current != null && current != Any::class.java) {
                current.declaredFields.forEach { field ->
                    val modifiers = field.modifiers
                    if (!field.isSynthetic &&
                        !Modifier.isStatic(modifiers) &&
                        !Modifier.isTransient(modifiers) &&
                        !field.name.endsWith("\$delegate")
                    ) {
                        require(field.trySetAccessible()) {
                            "Cannot access ${current.name}.${field.name} for MessagePack serialization"
                        }
                        add(field)
                    }
                }
                current = current.superclass
            }
        }
    }

    private fun packString(packer: org.msgpack.core.MessagePacker, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) {
            "MessagePack string exceeds the $MAX_STRING_BYTES-byte limit"
        }
        packer.packRawStringHeader(bytes.size)
        packer.writePayload(bytes)
    }

    private fun unpackValue(unpacker: MessageUnpacker, depth: Int, state: DecodeState): Any? {
        state.visit(depth)
        return when (unpacker.nextFormat.valueType) {
            ValueType.NIL -> unpacker.unpackNil().let { null }
            ValueType.BOOLEAN -> unpacker.unpackBoolean()
            ValueType.INTEGER -> try {
                unpacker.unpackLong()
            } catch (error: MessageIntegerOverflowException) {
                throw IllegalArgumentException("MessagePack integer exceeds the signed 64-bit profile", error)
            }
            ValueType.FLOAT -> {
                val number = unpacker.unpackDouble()
                require(number.isFinite()) { "MessagePack floating-point values must be finite" }
                number
            }
            ValueType.STRING -> unpackString(unpacker)
            ValueType.ARRAY -> {
                val size = unpacker.unpackArrayHeader()
                require(size <= MAX_CONTAINER_ITEMS) {
                    "MessagePack array exceeds the $MAX_CONTAINER_ITEMS-item limit"
                }
                List(size) { unpackValue(unpacker, depth + 1, state) }
            }
            ValueType.MAP -> {
                val size = unpacker.unpackMapHeader()
                require(size <= MAX_CONTAINER_ITEMS) {
                    "MessagePack map exceeds the $MAX_CONTAINER_ITEMS-entry limit"
                }
                LinkedHashMap<String, Any?>(size).apply {
                    repeat(size) {
                        state.visit(depth + 1)
                        require(unpacker.nextFormat.valueType == ValueType.STRING) {
                            "MessagePack map keys must be strings"
                        }
                        val key = unpackString(unpacker)
                        put(key, unpackValue(unpacker, depth + 1, state))
                    }
                }
            }
            ValueType.BINARY -> throw IllegalArgumentException("MessagePack binary values are not part of entity wire v1")
            ValueType.EXTENSION -> throw IllegalArgumentException("MessagePack extension values are not part of entity wire v1")
        }
    }

    private fun unpackString(unpacker: MessageUnpacker): String {
        val size = unpacker.unpackRawStringHeader()
        require(size <= MAX_STRING_BYTES) {
            "MessagePack string exceeds the $MAX_STRING_BYTES-byte limit"
        }
        val bytes = unpacker.readPayload(size)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("MessagePack string contains malformed UTF-8", error)
        }
    }

    private class EncodeState {
        var nodes: Int = 0
        val active = IdentityHashMap<Any, Boolean>()

        fun visit(depth: Int) {
            require(depth <= MAX_DEPTH) { "MessagePack graph exceeds the maximum depth of $MAX_DEPTH" }
            nodes++
            require(nodes <= MAX_NODES) { "MessagePack graph exceeds the $MAX_NODES-node limit" }
        }
    }

    private class DecodeState {
        var nodes: Int = 0

        fun visit(depth: Int) {
            require(depth <= MAX_DEPTH) { "MessagePack graph exceeds the maximum depth of $MAX_DEPTH" }
            nodes++
            require(nodes <= MAX_NODES) { "MessagePack graph exceeds the $MAX_NODES-node limit" }
        }
    }
}
