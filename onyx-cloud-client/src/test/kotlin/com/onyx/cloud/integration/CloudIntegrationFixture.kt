package com.onyx.cloud.integration

import com.onyx.cloud.impl.OnyxClient
import org.junit.Assume

/**
 * Explicit configuration gate for tests that call a real Onyx Cloud service.
 *
 * System properties take precedence over environment variables. Tests are skipped through
 * JUnit 4 assumptions until every setting required by the selected client is present.
 */
internal object CloudIntegrationFixture {
    private data class Setting(
        val propertyName: String,
        val environmentName: String,
    )

    private val baseUrl = Setting(
        propertyName = "onyx.cloud.integration.baseUrl",
        environmentName = "ONYX_CLOUD_INTEGRATION_BASE_URL",
    )
    private val databaseId = Setting(
        propertyName = "onyx.cloud.integration.databaseId",
        environmentName = "ONYX_CLOUD_INTEGRATION_DATABASE_ID",
    )
    private val apiKey = Setting(
        propertyName = "onyx.cloud.integration.apiKey",
        environmentName = "ONYX_CLOUD_INTEGRATION_API_KEY",
    )
    private val apiSecret = Setting(
        propertyName = "onyx.cloud.integration.apiSecret",
        environmentName = "ONYX_CLOUD_INTEGRATION_API_SECRET",
    )
    private val aiBaseUrl = Setting(
        propertyName = "onyx.cloud.integration.aiBaseUrl",
        environmentName = "ONYX_CLOUD_INTEGRATION_AI_BASE_URL",
    )

    /** Creates a client for database, schema, document, and secrets integration tests. */
    fun client(): OnyxClient = createClient(requireAiBaseUrl = false)

    /** Creates a client for AI integration tests, which require a separately configured endpoint. */
    fun aiClient(): OnyxClient = createClient(requireAiBaseUrl = true)

    private fun createClient(requireAiBaseUrl: Boolean): OnyxClient {
        val requiredSettings = buildList {
            add(baseUrl)
            add(databaseId)
            add(apiKey)
            add(apiSecret)
            if (requireAiBaseUrl) add(aiBaseUrl)
        }
        val values = requiredSettings.associateWith(::resolve)
        val missing = values.filterValues { it == null }.keys

        Assume.assumeTrue(
            "Skipping real Onyx Cloud integration test; configure ${missing.joinToString { "${it.propertyName} or ${it.environmentName}" }}",
            missing.isEmpty(),
        )

        val configuredBaseUrl = checkNotNull(values[baseUrl])
        return OnyxClient(
            baseUrl = configuredBaseUrl,
            databaseId = checkNotNull(values[databaseId]),
            apiKey = checkNotNull(values[apiKey]),
            apiSecret = checkNotNull(values[apiSecret]),
            aiBaseUrl = if (requireAiBaseUrl) {
                checkNotNull(values[aiBaseUrl])
            } else {
                resolve(aiBaseUrl) ?: configuredBaseUrl
            },
        )
    }

    private fun resolve(setting: Setting): String? =
        System.getProperty(setting.propertyName)?.takeIf(String::isNotBlank)
            ?: System.getenv(setting.environmentName)?.takeIf(String::isNotBlank)
}
