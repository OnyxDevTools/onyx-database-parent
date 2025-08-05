package com.onyxdevtools.ai.transformation

/**
 * A mutable implementation of the Vocabulary interface using in-memory hash maps.
 *
 * This implementation provides efficient bidirectional token-to-ID mapping using two
 * synchronized hash maps for O(1) average-case lookup and insertion performance.
 * The vocabulary dynamically grows as new tokens are encountered, making it ideal
 * for building vocabularies during text processing.
 *
 * **Key Features:**
 * - **Dynamic growth**: Automatically assigns sequential IDs to new tokens
 * - **Fast lookups**: O(1) average-case performance for both directions
 * - **Memory efficient**: Uses compact hash map storage
 * - **Thread-unsafe**: Not synchronized for concurrent access
 *
 * **ID Assignment:**
 * IDs are assigned sequentially starting from 0. The first token gets ID 0,
 * the second gets ID 1, and so on. This ensures dense ID space utilization
 * and predictable behavior.
 *
 * **Usage Patterns:**
 * - Building vocabularies during preprocessing
 * - Dynamic vocabulary expansion during training
 * - Converting between text and numerical representations
 * - BPE tokenization vocabulary management
 *
 * **Thread Safety:**
 * This implementation is not thread-safe. For concurrent access, external
 * synchronization or thread-safe alternatives should be used.
 *
 * @see Vocabulary
 * @see OnyxVocabulary
 */
class MutableVocabulary : Vocabulary {
    /** Map from token strings to their assigned integer IDs */
    private val tokenToId = mutableMapOf<String, Int>()
    
    /** Map from integer IDs back to their corresponding token strings */
    private val idToToken = mutableMapOf<Int, String>()

    /**
     * Gets or assigns an ID for the given token.
     *
     * If the token already exists, returns its existing ID. If the token is new,
     * assigns it the next sequential ID (equal to current vocabulary size) and
     * adds it to both internal maps.
     *
     * @param token The token to get or assign an ID for
     * @return The unique ID associated with this token
     */
    override fun getId(token: String): Int {
        return tokenToId.getOrPut(token) {
            val id = tokenToId.size
            idToToken[id] = token
            id
        }
    }

    /**
     * Retrieves the token string for a given ID.
     *
     * @param id The ID to look up
     * @return The token associated with the ID, or null if the ID doesn't exist
     */
    override fun getToken(id: Int): String? = idToToken[id]

    /**
     * Finds the ID for a token without adding it to the vocabulary.
     *
     * @param token The token to search for
     * @return The ID if the token exists, null otherwise
     */
    override fun findId(token: String): Int? = tokenToId[token]

    /**
     * The current number of unique tokens in the vocabulary.
     *
     * Since IDs are assigned sequentially starting from 0, the size equals
     * the next ID that would be assigned to a new token.
     */
    override val size: Int get() = tokenToId.size

    /**
     * Explicitly adds a token to the vocabulary.
     *
     * This method ensures the token is present by calling getId(), which
     * will add the token if it doesn't already exist.
     *
     * @param token The token to add to the vocabulary
     */
    override fun addToken(token: String) {
        getId(token) // Adds if not present
    }
}
