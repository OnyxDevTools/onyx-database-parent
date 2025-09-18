package com.onyx.cloud.integration

import com.onyx.cloud.OnyxClient
import com.onyx.cloud.api.*
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Integration tests that exercise aggregate select functions against the
 * real Onyx Cloud backend.
 */
class SelectFunctionsIntegrationTest {
    private val client = OnyxClient(
        baseUrl = "https://api.onyx.dev",
        databaseId = "bbabca0e-82ce-11f0-0000-a2ce78b61b6a",
        apiKey = "Hj52NXaqB",
        apiSecret = "bEJiEsuE28z1XeT/MHujy+1/6sqFMsZ4WK7M/M8BS34="
    )

    private fun safeDelete(table: String, id: String?) {
        if (id == null) return
        try {
            client.delete(table, id)
        } catch (_: Exception) {
            // Ignore missing records so cleanup always succeeds
        }
    }

    private fun newUser(now: Date) = User(
        id = UUID.randomUUID().toString(),
        username = "agg-user-${UUID.randomUUID().toString().substring(0, 8)}",
        email = "agg${UUID.randomUUID().toString().substring(0, 8)}@example.com",
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    private fun newProfile(userId: String, now: Date, lastName: String, age: Int) = UserProfile(
        id = UUID.randomUUID().toString(),
        userId = userId,
        firstName = "Aggregate",
        lastName = lastName,
        createdAt = now,
        updatedAt = now,
        age = age
    )

    @Test
    fun selectAggregateFunctionsForUserProfiles() {
        val now = Date()
        val marker = "agg-${UUID.randomUUID().toString().substring(0, 8)}"
        val ages = listOf(21, 42, 63)

        val savedPairs = ages.map { age ->
            val user = client.save(newUser(now))
            val profile = client.save(newProfile(user.id!!, now, marker, age))
            user to profile
        }

        try {
            val results = client.from<UserProfile>()
                .select(
                    min("age"),
                    max("age"),
                    avg("age"),
                    sum("age"),
                    count("age"),
                    median("age"),
                    std("age"),
                    variance("age"),
                    percentile("age", 75.0),
                )
                .where("lastName" eq marker)
                .list<Map<String, Any?>>()
                .getAllRecords()

            val record = results.firstOrNull() ?: fail("Expected aggregation results for marker $marker")

            val minAge = (record["min(age)"] as Number).toInt()
            val maxAge = (record["max(age)"] as Number).toInt()
            val sumAges = (record["sum(age)"] as Number).toInt()
            val avgAge = (record["avg(age)"] as Number).toDouble()
            val countAge = (record["count(age)"] as Number).toInt()
            val medianAge = (record["median(age)"] as Number).toDouble()
            val stdAge = (record["std(age)"] as Number).toDouble()
            val varianceAge = (record["variance(age)"] as Number).toDouble()
            val percentileAge = (record["percentile(age,75.0)"] as Number).toDouble()

            assertEquals(21, minAge, "Unexpected minimum age")
            assertEquals(63, maxAge, "Unexpected maximum age")
            assertEquals(126, sumAges, "Unexpected sum of ages")
            assertEquals(42.0, avgAge, 0.001, "Unexpected average age")
            assertEquals(ages.size, countAge, "Unexpected count of ages")
            assertEquals(42.0, medianAge, 0.001, "Unexpected median age")
            assertEquals(17.146428199482248, stdAge, 0.001, "Unexpected standard deviation")
            assertEquals(294.0, varianceAge, 0.001, "Unexpected variance")
            assertEquals(52.0, percentileAge, 0.001, "Unexpected 75th percentile")
        } finally {
            savedPairs.forEach { (user, profile) ->
                safeDelete("UserProfile", profile.id)
                safeDelete("User", user.id)
            }
        }
    }

    @Test
    fun selectStringFunctionsForUserProfiles() {
        val now = Date()
        val marker = "func-${UUID.randomUUID().toString().substring(0, 8)}"
        val lastName = "Function-$marker"

        val user = client.save(newUser(now))
        val profile = client.save(newProfile(user.id!!, now, lastName, 33))

        try {
            val results = client.from<UserProfile>()
                .select(
                    upper("lastName"),
                    lower("lastName"),
                    substring("lastName", 0, "Function".length),
                    replace("lastName", "-", "_"),
                )
                .where("lastName" eq lastName)
                .list<Map<String, Any?>>()
                .getAllRecords()

            val record = results.firstOrNull() ?: fail("Expected string function results for marker $marker")

            assertEquals(lastName.uppercase(), record["upper(lastName)"]?.toString(), "Unexpected uppercase result")
            assertEquals(lastName.lowercase(), record["lower(lastName)"]?.toString(), "Unexpected lowercase result")
            assertEquals("Function", record["substring(lastName,0,8)"]?.toString(), "Unexpected substring result")
            assertEquals(lastName.replace("-", "_"), record["replace(lastName,-,_)"]?.toString(), "Unexpected replace result")
        } finally {
            safeDelete("UserProfile", profile.id)
            safeDelete("User", user.id)
        }
    }
}

