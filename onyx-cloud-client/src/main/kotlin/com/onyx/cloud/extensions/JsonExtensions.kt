package com.onyx.cloud.extensions

import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.onyx.cloud.api.QueryResultsImpl
import kotlin.reflect.KClass
import com.google.gson.*
import java.lang.reflect.Type

/**
 * Serializes the receiving object into its equivalent JSON representation.
 *
 * This uses a pre-configured Gson instance that handles cyclical references,
 * specific date formats, and excludes Kotlin delegate fields.
 *
 * @receiver The object to serialize.
 * @return A JSON string representation of the object.
 */
fun Any.toJson(): String = gson.toJson(this)

/**
 * Deserializes the JSON string into an object of the specified type `T`.
 *
 * Handles potential `JsonSyntaxException` and other parsing errors gracefully by returning null.
 * Uses a pre-configured Gson instance.
 *
 * @receiver The JSON string to deserialize.
 * @return An object of type `T` or `null` if deserialization fails.
 * @param T The target type to deserialize into.
 */
inline fun <reified T> String.fromJson(): T? {
    return try {
        gson.fromJson(this, object : TypeToken<T>() {}.type)
    } catch (_: Exception) {
        // Log error or handle specific exceptions if needed, but avoid printStackTrace in library code
        // e.g., Log.e("JsonExtensions", "Failed to deserialize JSON string", e)
        null // Return null on any deserialization error
    }
}

/**
 * Deserializes the JSON string into an object of the specified type provided by `KClass`.
 *
 * **Warning:** This involves an unchecked cast. Ensure the JSON string actually represents
 * an object assignable to the type represented by `type`. A `ClassCastException`
 * might occur at runtime if the types mismatch. Prefer the reified `fromJson<T>()`
 * function when possible for better type safety.
 *
 * @receiver The JSON string to deserialize.
 * @param type The Kotlin class (`KClass`) representing the target type.
 * @return An object of the specified type.
 * @throws JsonSyntaxException if the string is not valid JSON for the target type.
 * @throws ClassCastException if the deserialized object cannot be cast to `T`.
 */
@Suppress("UNCHECKED_CAST")
fun <T> String.fromJson(type: KClass<*>): T {
    // Consider adding try-catch here as well if null safety or specific error handling is desired.
    // The current implementation will throw exceptions directly.
    return gson.fromJson(this, type.java) as T
}

/**
 * Deserializes the JSON string into a list of objects of the specified element type.
 *
 * Assumes the JSON string represents a JSON array. If the string is not a valid JSON array
 * or if any other parsing error occurs, this function returns `null`.
 *
 * @receiver The JSON string representing a JSON array.
 * @param elementType The `Class` of the elements within the list.
 * @return A `List<T>` containing the deserialized objects, or `null` if the input is not a
 * valid JSON array or another error occurs during deserialization.
 * @param T The type of elements in the list.
 */
fun <T> String.fromJsonList(elementType: Class<*>): List<T>? {
    return try {
        val listType = TypeToken.getParameterized(List::class.java, elementType).type
        gson.fromJson(this, listType)
    } catch (_: JsonSyntaxException) {
        // Return null specifically if the JSON syntax is wrong (e.g., not an array)
        null
    } catch (_: Exception) {
        // Catch other potential errors during deserialization
        // Log error if needed
        null
    }
}

class QueryResultsImplAdapter : JsonDeserializer<QueryResultsImpl<*>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): QueryResultsImpl<*> {
        val obj = json.asJsonObject
        val records = obj.getAsJsonArray("records") ?: JsonArray()
        val next = obj.get("nextPage")?.takeIf { !it.isJsonNull }?.asString
        val total = obj.get("totalResults")?.asInt
            ?: obj.get("totalRecords")?.asInt
            ?: 0
        return QueryResultsImpl<Any>(recordText = records, nextPage = next, totalResults = total)
    }
}

// usage without reified:
fun <T: Any> String.toQueryResults(gson: Gson, entityType: KClass<*>): QueryResultsImpl<T> {
    val type = TypeToken.getParameterized(QueryResultsImpl::class.java, entityType.java).type
    val page = gson.fromJson(this, type) as QueryResultsImpl<T>
    page.classType = entityType
    return page
}