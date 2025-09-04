package dev.onyx.ai.transformation

/**
 * A tokenizer that breaks text into tokens.
 */
interface Tokenizer {
    /**
     * Tokenizes a single sequence of text.
     *
     * @param text The text to tokenize.
     * @return A list of tokens.
     */
    fun tokenize(text: String): List<String>

    /**
     * Encodes a single sequence of text into a list of token IDs.
     *
     * @param text The text to encode.
     * @return A list of token IDs.
     */
    fun encode(text: String): List<Int>

    /**
     * Encodes a pair of sequences into a list of token IDs.
     *
     * @param text The first sequence of text.
     * @return A list of token IDs.
     */
    fun encode(text: String, textPair: String): List<Int>

    /**
     * Decodes a list of token IDs back into a single string.
     *
     * @param ids The list of token IDs to decode.
     * @return The decoded string.
     */
    fun decode(ids: List<Int>): String
}
