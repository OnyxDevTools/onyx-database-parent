package com.onyx.cloud

import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.integration.User
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdatePayloadSerializationTest {
    @Test
    fun numericUpdateValuesAreConvertedToStrings() {
        val client =
            OnyxClient(baseUrl = "https://example.com", databaseId = "db", apiKey = "key", apiSecret = "secret")
        val builder = client.from<User>().setUpdates("age" to 30)

        val method = builder::class.java.getDeclaredMethod("buildUpdateQueryPayload")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val payload = method.invoke(builder) as Map<String, Any?>
        val updates = payload["updates"] as Map<*, *>

        assertEquals("30", updates["age"])
    }
}

