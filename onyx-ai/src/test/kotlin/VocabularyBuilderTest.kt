package com.onyxdevtools.ai

import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.MutableVocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import kotlin.test.Test
import kotlin.test.assertTrue

class VocabularyBuilderTest {

    @Test
    fun testBuildVocabularyFromText() {
        val text = """
            Alice was beginning to get very tired of sitting by her sister on the bank, 
            and of having nothing to do: once or twice she had peeped into the book her 
            sister was reading, but it had no pictures or conversations in it.
        """.trimIndent()

        // Build vocabulary using the new function
        val vocabulary = MutableVocabulary()
        vocabulary.appendToVocabulary(text)

        // Test that vocabulary contains expected tokens
        assertTrue(vocabulary.size > 0, "Vocabulary should not be empty")

        // Check for special tokens
        assertTrue(vocabulary.findId("[PAD]") != null, "Should contain [PAD] token")
        assertTrue(vocabulary.findId("[CLS]") != null, "Should contain [CLS] token")
        assertTrue(vocabulary.findId("[SEP]") != null, "Should contain [SEP] token")
        assertTrue(vocabulary.findId("[UNK]") != null, "Should contain [UNK] token")

        // Check for some words from the text
        assertTrue(vocabulary.findId("Alice") != null, "Should contain 'Alice'")
        assertTrue(vocabulary.findId("was") != null, "Should contain 'was'")
        assertTrue(vocabulary.findId("tired") != null, "Should contain 'tired'")

        println("Vocabulary size: ${vocabulary.size}")
        println("Sample tokens found:")
        listOf("Alice", "was", "tired", "sister", "book").forEach { token ->
            val id = vocabulary.findId(token)
            if (id != null) {
                println("  '$token' -> ID: $id")
            }
        }
    }

    @Test
    fun testAppendToVocabulary() {
        // Start with a vocabulary built from one text
        val initialText = "Hello world! This is a test."
        val vocabulary = MutableVocabulary()
        vocabulary.appendToVocabulary(initialText)

        val initialSize = vocabulary.size
        println("Initial vocabulary size: $initialSize")

        // Append tokens from new text
        val newText = "Programming with Kotlin is fun and exciting!"
        vocabulary.appendToVocabulary(newText)

        val finalSize = vocabulary.size
        println("Final vocabulary size: $finalSize")

        // Verify the vocabulary has grown
        assertTrue(finalSize > initialSize, "Vocabulary should have grown after appending new text")

        // Check that new tokens were added 
        assertTrue(vocabulary.findId("Programming") != null, "Should contain 'Programming'")
        assertTrue(vocabulary.findId("Kotlin") != null, "Should contain 'Kotlin'")
        assertTrue(vocabulary.findId("exciting") != null, "Should contain 'exciting'")

        // Check that old tokens are still there
        assertTrue(vocabulary.findId("Hello") != null, "Should still contain 'Hello'")
        assertTrue(vocabulary.findId("world") != null, "Should still contain 'world'")

        println("New tokens added:")
        listOf("Programming", "Kotlin", "exciting", "fun").forEach { token ->
            val id = vocabulary.findId(token)
            if (id != null) {
                println("  '$token' -> ID: $id")
            }
        }
    }

    @Test
    fun testTokenizationWithBuiltVocabulary() {
        val text = "fun main() { println(\"Hello Kotlin!\") }"
        val vocabulary = MutableVocabulary()
        vocabulary.appendToVocabulary(text)
        val tokenizer = BPETokenizer(vocabulary)

        // Tokenize the text
        val tokens = tokenizer.tokenize(text)
        println("Original text: $text")
        println("Tokens: $tokens")

        // Verify tokenization works
        assertTrue(tokens.isNotEmpty(), "Should produce tokens")
        assertTrue(tokens.contains("fun"), "Should contain 'fun' token")
        assertTrue(tokens.contains("main"), "Should contain 'main' token")
        assertTrue(tokens.contains("println"), "Should contain 'println' token")

        // Test encoding/decoding
        val encoded = tokenizer.encode(text)
        val decoded = tokenizer.decode(encoded)
        println("Encoded: $encoded")
        println("Decoded: $decoded")

        assertTrue(encoded.isNotEmpty(), "Should produce encoded IDs")
    }
}
