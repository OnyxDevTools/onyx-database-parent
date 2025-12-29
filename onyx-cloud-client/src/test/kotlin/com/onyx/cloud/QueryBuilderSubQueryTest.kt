package com.onyx.cloud

import com.onyx.cloud.api.eq
import com.onyx.cloud.api.inOp
import com.onyx.cloud.extensions.gson
import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.impl.QueryCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class QueryBuilderSubQueryTest {
    @Test
    fun subQueriesAreSerializedInConditions() {
        val client = OnyxClient(baseUrl = "https://example.com", databaseId = "db", apiKey = "key", apiSecret = "secret")

        val subQuery = client
            .from<Department>()
            .select("id")
            .where("active".eq(true))

        val builder = client
            .from<Employee>()
            .where("departmentId".inOp(subQuery))

        val method = builder::class.java.getDeclaredMethod("buildSelectQueryPayload")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val payload = method.invoke(builder) as Map<String, Any?>

        val condition = assertIs<QueryCondition.SingleCondition>(payload["conditions"])
        val nestedQuery = condition.criteria.value as Map<*, *>

        assertEquals("SelectQuery", nestedQuery["type"])
        assertEquals(Department::class.simpleName, nestedQuery["table"])
        assertEquals(listOf("id"), nestedQuery["fields"])
        assertNotNull(nestedQuery["conditions"])

        val json = gson.toJson(payload)
        assertFalse(json.contains("cyclicReference", ignoreCase = true))
    }
}

private data class Department(val id: String, val active: Boolean)
private data class Employee(val id: String, val departmentId: String)
