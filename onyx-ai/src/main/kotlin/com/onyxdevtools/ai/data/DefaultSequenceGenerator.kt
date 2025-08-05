package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.transformation.Vocabulary

/**
 * Default implementation of SequenceGenerator for creating training sequences from tokenized text.
 *
 * This implementation creates sliding window sequences from input tokens and converts them
 * into a format suitable for neural network training. Key features include:
 *
 * - **Shuffled Training Order**: Randomizes the order of generated sequences to improve training
 * - **One-Hot Encoded Targets**: Converts target tokens to one-hot vectors based on vocabulary size
 * - **Sliding Window Approach**: Creates overlapping sequences with configurable stride
 * - **Lazy Evaluation**: Uses Kotlin's sequence builder for memory-efficient processing
 *
 * The generated sequences follow a standard language modeling pattern where:
 * - Input sequence: tokens[i..i+seqLength-1] as raw token IDs
 * - Target sequence: tokens[i+1..i+seqLength] as one-hot encoded vectors
 *
 * This creates a "next token prediction" training setup commonly used in
 * autoregressive language models like GPT.
 *
 * @param vocabulary The vocabulary used to determine the size of one-hot encoded target vectors.
 *                   Must contain all tokens that appear in the input token sequences.
 * @see SequenceGenerator
 * @see Vocabulary
 */
class DefaultSequenceGenerator(private val vocabulary: Vocabulary) : SequenceGenerator {
    /**
     * Generates training sequences from tokens with shuffled order and one-hot encoded targets.
     *
     * Creates sliding window sequences where each input is paired with its corresponding
     * target sequence (shifted by one position). The sequences are generated in random
     * order to improve training convergence and prevent overfitting to data ordering.
     *
     * Implementation details:
     * - Input sequences contain raw token IDs converted to doubles
     * - Target sequences are one-hot encoded based on vocabulary size
     * - Sequences are shuffled for better training dynamics
     * - Uses lazy sequence generation for memory efficiency
     *
     * @param tokens List of integer tokens representing the tokenized text.
     *               Each token ID must be valid within the vocabulary range [0, vocabulary.size).
     * @param seqLength The length of each generated sequence. Determines the context window
     *                  size for the language model training.
     * @param stride The step size between consecutive sequence starts. Smaller values
     *               create more overlapping sequences but increase dataset size.
     * @return A lazy Sequence of training pairs where:
     *         - First element: Input sequence as DoubleArray of token IDs
     *         - Second element: Target sequences as Array<DoubleArray> of one-hot vectors
     *         Each target vector has size equal to vocabulary.size.
     * @throws IllegalArgumentException if tokens contain IDs outside vocabulary range
     * @throws IndexOutOfBoundsException if seqLength is larger than available tokens
     */
    override fun generateSequences(tokens: List<Int>, seqLength: Int, stride: Int): Sequence<Pair<DoubleArray, Array<DoubleArray>>> {
        return sequence {
            val indices = (0 until tokens.size - seqLength step stride).shuffled()
            for (i in indices) {
                val inputSeq = tokens.subList(i, i + seqLength).map { it.toDouble() }.toDoubleArray()
                val targetSeq = tokens.subList(i + 1, i + 1 + seqLength).map { targetToken ->
                    DoubleArray(vocabulary.size) { if (it == targetToken) 1.0 else 0.0 }
                }.toTypedArray()
                yield(inputSeq to targetSeq)
            }
        }
    }
}
