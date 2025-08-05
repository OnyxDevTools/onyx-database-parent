package com.onyxdevtools.ai.transformation

/**
 * A vocabulary that maps tokens to their corresponding IDs.
 */
interface Vocabulary {
    /**
     * Gets the ID for a given token. If the token does not exist in the vocabulary, it is added.
     *
     * @param token The token to get the ID for.
     * @return The ID of the token.
     */
    fun getId(token: String): Int

    /**
     * Gets the token for a given ID.
     *
     * @param id The ID of the token to get.
     * @return The token, or null if the ID does not exist.
     */
    fun getToken(id: Int): String?

    /**
     * Finds the ID for a given token without adding it.
     *
     * @param token The token to find.
     * @return The ID of the token, or null if it does not exist.
     */
    fun findId(token: String): Int?

    fun addToken(token: String)

    val size: Int
}
