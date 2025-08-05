package com.onyxdevtools.ai.transformation

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

    // Extract unique tokens from the text
    val uniqueTokens = tokenizer.extractUniqueTokens(text)
    
    // Add all discovered tokens to the vocabulary
    uniqueTokens.forEach { token ->
        // Only add if not already present (getId will add if not present)
        this.addToken(token)
    }
}
