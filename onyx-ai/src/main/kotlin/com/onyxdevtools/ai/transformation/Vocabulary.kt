package com.onyxdevtools.ai.transformation

/**
 * Interface defining a vocabulary that maps between tokens and their corresponding integer IDs.
 *
 * A vocabulary is a fundamental component in natural language processing that maintains
 * a bidirectional mapping between text tokens (strings) and unique integer identifiers.
 * This mapping is essential for converting human-readable text into numerical representations
 * that neural networks can process.
 *
 * Key characteristics:
 * - **Bidirectional mapping**: Convert tokens to IDs and IDs back to tokens
 * - **Dynamic growth**: Support for adding new tokens during processing
 * - **Unique identifiers**: Each token gets exactly one unique ID
 * - **Consistent ordering**: IDs are typically assigned sequentially
 *
 * Common use cases include:
 * - Text preprocessing for machine learning models
 * - BPE (Byte Pair Encoding) tokenization
 * - Language model vocabulary management
 * - Text-to-sequence conversion
 *
 * The vocabulary serves as the bridge between raw text and the numerical inputs
 * required by neural networks, enabling efficient processing of textual data.
 *
 * @see MutableVocabulary
 * @see OnyxVocabulary
 * @see BPETokenizer
 */
interface Vocabulary {
    /**
     * Gets the unique ID for a given token, automatically adding it if not present.
     *
     * This method provides both lookup and insertion functionality. If the token
     * already exists in the vocabulary, its existing ID is returned. If the token
     * is new, it gets assigned the next available ID and is added to the vocabulary.
     *
     * The auto-insertion behavior makes this method convenient for building
     * vocabularies dynamically during text processing.
     *
     * @param token The text token to get or assign an ID for. Should not be null.
     * @return The unique integer ID associated with the token. IDs are typically
     *         assigned sequentially starting from 0.
     */
    fun getId(token: String): Int = findId(token) ?: findId("[UNK]")!!

    /**
     * Retrieves the token associated with a given ID.
     *
     * Performs the reverse lookup from ID to token string. This is essential
     * for converting model outputs (which are typically ID sequences) back
     * to human-readable text.
     *
     * @param id The integer ID to look up. Should be a valid ID within the
     *           vocabulary range [0, size).
     * @return The token string associated with the ID, or null if the ID
     *         doesn't exist in the vocabulary.
     */
    fun getToken(id: Int): String?

    /**
     * Finds the ID for a token without modifying the vocabulary.
     *
     * Unlike getId(), this method performs a pure lookup operation and will
     * not add the token if it doesn't exist. This is useful when you need
     * to check if a token exists without modifying the vocabulary state.
     *
     * @param token The token to search for in the vocabulary.
     * @return The ID of the token if it exists, or null if the token
     *         is not found in the vocabulary.
     */
    fun findId(token: String): Int?

    /**
     * Explicitly adds a token to the vocabulary.
     *
     * Ensures the token is present in the vocabulary by adding it if necessary.
     * This method is useful for pre-populating vocabularies with known tokens
     * or ensuring specific tokens are available.
     *
     * @param token The token to add to the vocabulary. If the token already
     *              exists, this operation has no effect.
     */
    fun addToken(token: String)

    /**
     * The current number of unique tokens in the vocabulary.
     *
     * This property returns the total count of unique tokens currently stored
     * in the vocabulary. It's equivalent to the highest assigned ID plus one,
     * assuming IDs are assigned sequentially starting from 0.
     *
     * @return The size of the vocabulary as a non-negative integer.
     */
    val size: Int

    /**
     * Commit the vocabulary:
     *  - Keep only the top [maxTokens] by frequency (desc), tie-break by token (asc).
     *  - Delete all rows, reinsert the top set in order so SEQUENCE assigns fresh IDs.
     *  - Clear and rebuild L1 caches.
     *
     * NOTE: If you must reset the underlying sequence to start at 1, call the appropriate
     * store-specific reset here after delete (not shown).
     */
    fun commit(maxTokens: Int = 40_000) = Unit
}