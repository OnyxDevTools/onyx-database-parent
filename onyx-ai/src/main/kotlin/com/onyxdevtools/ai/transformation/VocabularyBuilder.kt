package dev.onyx.ai.transformation

/**
 * Extension function to append tokens from text to an existing vocabulary.
 * Uses BPE tokenization to discover new tokens and adds them to the vocabulary.
 */
fun Vocabulary.appendToVocabulary(text: String) {

    // Create tokenizer with current vocabulary
    val tokenizer = BPETokenizer(this)

    if (this.size == 0) {
        tokenizer.defaultTokens.forEach {
            addToken(it)
        }
        addToken(" ")
    }

    // Extract token frequencies from the text
    val tokenFreqs = tokenizer.extractTokenFrequencies(text)

    // Add all discovered tokens to the vocabulary and update frequencies
    tokenFreqs.forEach { (token, count) ->
        // Only add if not already present (getId will add if not present)
        this.addToken(token)
        (this as? OnyxVocabulary)?.incrementFrequency(token, count.toLong())
    }
}