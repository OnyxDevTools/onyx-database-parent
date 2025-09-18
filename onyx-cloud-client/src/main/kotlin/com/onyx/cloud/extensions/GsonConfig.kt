@file:Suppress("DEPRECATION")
package com.onyx.cloud.extensions

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.onyx.cloud.api.QueryResultsImpl
import java.text.SimpleDateFormat
import java.util.*

/**
 * A thread-local `SimpleDateFormat` used for formatting `java.util.Date` objects during serialization.
 * Ensures thread safety when formatting dates.
 *
 * **Note:** The format "MM/dd/yyyy hh:mm:ss a z" (e.g., 11/23/2024 10:57:47 PM PST) uses timezone names
 * which can be ambiguous. Consider using an ISO 8601 format with offsets (e.g., "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
 * for better interoperability if possible.
 */
private val threadLocalDateTimeFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("MM/dd/yyyy hh:mm:ss a z", Locale.US) }

/**
 * A Gson `TypeAdapterFactory` designed to detect and handle cyclical references during serialization.
 *
 * When a cycle is detected (an object is encountered again during its own serialization),
 * it outputs a placeholder JSON object `{"cyclicReference": "detected"}` instead of recursing indefinitely.
 *
 * This uses a `ThreadLocal` `IdentityHashMap` to track visited objects within a single serialization operation
 * on a given thread. Deserialization is delegated to Gson's default behavior.
 */
private class CyclicTypeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T> {
        val delegate = gson.getDelegateAdapter(this, type) // Get the default adapter

        return object : TypeAdapter<T>() {
            // ThreadLocal to track visited objects for the current serialization call on this thread.
            // IdentityHashMap uses reference equality (==) to detect cycles accurately.
            private val visitedObjects = ThreadLocal<IdentityHashMap<Any, Any>>()

            override fun write(out: JsonWriter, value: T?) {
                if (value == null) {
                    out.nullValue()
                    return
                }

                var isRootCall = false
                var threadVisited = visitedObjects.get()
                if (threadVisited == null) {
                    threadVisited = IdentityHashMap()
                    visitedObjects.set(threadVisited)
                    isRootCall = true // This is the top-level call for this object graph on this thread
                }

                if (threadVisited.containsKey(value)) {
                    // Cycle detected! Output placeholder instead of recursing.
                    out.beginObject()
                    out.name("cyclicReference").value("detected - [${value.javaClass.name}]") // More info
                    out.endObject()
                    return // Stop serialization for this branch
                }

                // Mark object as visited *before* delegating serialization
                threadVisited[value] = value // Value itself doesn't matter, just the key presence

                try {
                    // Delegate the actual writing to Gson's default adapter
                    delegate.write(out, value)
                } finally {
                    // Clean up: remove the object from the visited map *after* its serialization is complete
                    threadVisited.remove(value)
                    // If this was the root call, clean up the ThreadLocal entirely
                    if (isRootCall) {
                        visitedObjects.remove()
                    }
                }
            }

            override fun read(reader: JsonReader): T? {
                // Deserialization doesn't typically suffer from cycles in the same way.
                // Delegate to the default adapter. If cycle placeholders were written,
                // they might cause issues here if not handled (e.g., expecting specific fields).
                // The default adapter might throw if it encounters the placeholder unexpectedly.
                return delegate.read(reader)
            }
        }
    }
}

/**
 * Exclusion strategy to prevent serialization/deserialization of Kotlin's synthetic delegate fields (ending in "$delegate").
 * This avoids redundant data in the JSON output for delegated properties.
 */
private val kotlinDelegateExclusionStrategy = object : ExclusionStrategy {
    override fun shouldSkipField(f: FieldAttributes): Boolean {
        // Skip fields backing Kotlin delegated properties (e.g., val name: String by lazy { ... })
        return f.name.endsWith("\$delegate")
    }

    override fun shouldSkipClass(clazz: Class<*>): Boolean = false
}

/**
 * A pre-configured singleton instance of `Gson` for use throughout the application.
 *
 * Configuration includes:
 * - Enabling complex map key serialization.
 * - Serializing special floating-point values (NaN, Infinity).
 * - Omitting null fields to avoid sending explicit nulls for unset properties.
 * - Excluding Kotlin synthetic delegate fields (`$delegate`).
 * - Registering a `CyclicTypeAdapterFactory` to handle object graph cycles.
 * - Registering custom adapters for `java.util.Date` serialization and deserialization.
 * - Setting lenient parsing mode.
 */
val gson: Gson = GsonBuilder()
    .enableComplexMapKeySerialization() // Allow non-primitive map keys
    .serializeSpecialFloatingPointValues() // Handle NaN, Infinity
    .serializeNulls()
    .addSerializationExclusionStrategy(kotlinDelegateExclusionStrategy)
    .addDeserializationExclusionStrategy(kotlinDelegateExclusionStrategy)
    .registerTypeAdapterFactory(CyclicTypeAdapterFactory()) // Handle cyclical references
    .registerTypeAdapter(QueryResultsImpl::class.java, QueryResultsImplAdapter())
    .registerTypeAdapter(Date::class.java, JsonSerializer<Date> { src, _, _ ->
        // Serialize Date using the thread-local formatter
        JsonPrimitive(threadLocalDateTimeFormat.get().format(src))
    })
    .registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
        // Deserialize Date using the flexible parseToJavaDate extension
        json.asString.parseToJavaDate()
            ?: throw JsonParseException("Failed to parse date: '${json.asString}' using supported formats.")
    })
    .setLenient() // Allow minor JSON syntax deviations (use with caution)
    .create()
