package com.onyx.cloud.extensions

import com.onyx.cloud.api.FetchInit
import com.onyx.cloud.api.FetchResponse
import com.onyx.cloud.impl.OnyxClient
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private data class SampleRecord(val value: Int)

class SequenceExtensionTest {

    @Test
    fun `asSequence streams pages lazily with filtering and mapping`() {
        val responses = ArrayDeque<StubFetch.Response>()
        responses += StubFetch.Response.Success(pageJson(listOf(1, 2), "page-2"))
        responses += StubFetch.Response.Success(pageJson(listOf(3, 4), "page-3"))
        responses += StubFetch.Response.Success(pageJson(listOf(5), null))

        val fetch = StubFetch(responses)
        val client = OnyxClient(
            baseUrl = "https://example.com",
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            fetch = fetch::invoke,
        )

        val executor = Executors.newSingleThreadExecutor()
        try {
            val firstPage = client.from<SampleRecord>().list<SampleRecord>()
            val values = firstPage.asSequence(
                executor = executor,
                filter = { it.value % 2 == 0 },
                transform = { it.value * 10 },
            ).toList()

            assertEquals(listOf(20, 40), values)
            assertEquals(
                listOf(
                    "https://example.com/data/db/query/SampleRecord",
                    "https://example.com/data/db/query/SampleRecord?nextPage=page-2",
                    "https://example.com/data/db/query/SampleRecord?nextPage=page-3",
                ),
                fetch.requestedUrls,
            )
            assertTrue(fetch.isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `asSequence propagates failures from prefetched pages`() {
        val responses = ArrayDeque<StubFetch.Response>()
        responses += StubFetch.Response.Success(pageJson(listOf(1, 2), "page-2"))
        responses += StubFetch.Response.Failure(IllegalStateException("boom"))

        val fetch = StubFetch(responses)
        val client = OnyxClient(
            baseUrl = "https://example.com",
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            fetch = fetch::invoke,
        )
        val executor = Executors.newSingleThreadExecutor()

        try {
            val firstPage = client.from<SampleRecord>().list<SampleRecord>()
            val sequence: Sequence<SampleRecord> = firstPage.asSequence(executor = executor)
            val iterator = sequence.iterator()

            assertEquals(SampleRecord(1), iterator.next())
            assertEquals(SampleRecord(2), iterator.next())
            val error = assertFailsWith<IllegalStateException> { iterator.next() }
            assertEquals("boom", error.message)
            assertEquals(
                listOf(
                    "https://example.com/data/db/query/SampleRecord",
                    "https://example.com/data/db/query/SampleRecord?nextPage=page-2",
                ),
                fetch.requestedUrls,
            )
            assertTrue(fetch.isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        fun pageJson(values: List<Int>, nextPage: String?): String = buildString {
            append('{')
            append("\"records\":")
            append('[')
            append(values.joinToString(",") { "{\"value\":$it}" })
            append(']')
            if (nextPage != null) {
                append(",\"nextPage\":\"")
                append(nextPage)
                append('\"')
            }
            append(",\"totalResults\":")
            append(values.size)
            append('}')
        }
    }
}

private class StubFetch(private val responses: ArrayDeque<StubFetch.Response>) {
    val requestedUrls = mutableListOf<String>()

    fun invoke(url: String, init: FetchInit?): FetchResponse {
        requestedUrls += url
        val action = responses.pollFirst()
            ?: throw AssertionError("No stubbed response for $url")
        return when (action) {
            is Response.Success -> ResponseImpl(action.body, action.status)
            is Response.Failure -> throw action.throwable
        }
    }

    fun isEmpty(): Boolean = responses.isEmpty()

    sealed class Response {
        data class Success(val body: String, val status: Int = 200) : Response()
        data class Failure(val throwable: Throwable) : Response()
    }

    private class ResponseImpl(
        private val payload: String,
        private val statusCode: Int,
    ) : FetchResponse {
        override val ok: Boolean get() = statusCode in 200..299
        override val status: Int get() = statusCode
        override val statusText: String get() = statusCode.toString()
        override fun header(name: String): String? = null
        override fun text(): String = payload
        override val body: Any? get() = payload
    }
}
