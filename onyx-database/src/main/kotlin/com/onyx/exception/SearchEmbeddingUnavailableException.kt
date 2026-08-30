package com.onyx.exception

/**
 * Semantic search could not run because its server-side embedding capability is unavailable.
 *
 * This is distinct from an invalid SEARCH request so remote servers can return an actionable
 * capability/service response without exposing unrelated internal failures.
 */
class SearchEmbeddingUnavailableException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : OnyxException(message, cause)
