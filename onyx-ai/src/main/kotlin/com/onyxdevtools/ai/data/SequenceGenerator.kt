package dev.onyx.ai.data

import dev.onyx.ai.transformation.Vocabulary
import dev.onyx.ai.Tensor

/**
 * Sealed class representing different target representations for sequence generation.
 * Allows generators to return either dense Tensor vectors or sparse token IDs.
 */
sealed class SequenceTarget {
    /**
     * Dense target representation using one-hot encoded vectors.
     * Memory usage: O(vocab_size × seq_length) per training example.
     * Compatible with standard categorical cross-entropy loss.
     */
    data class Dense(val vectors: Tensor) : SequenceTarget() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Dense
            if (vectors.size != other.vectors.size || vectors.columnSize != other.vectors.columnSize) return false
            for (r in 0 until vectors.size) {
                for (c in 0 until vectors.columnSize) {
                    if (vectors[r, c] != other.vectors[r, c]) return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var result = vectors.size
            result = 31 * result + vectors.columnSize
            for (r in 0 until vectors.size) {
                for (c in 0 until vectors.columnSize) {
                    result = 31 * result + vectors[r, c].hashCode()
                }
            }
            return result
        }
    }

    /**
     * Sparse target representation using token IDs.
     * Memory usage: O(seq_length) per training example.
     * Compatible with sparse categorical cross-entropy loss.
     * Uses -1 to indicate positions that should be ignored in loss computation.
     */
    data class Sparse(val tokenIds: IntArray) : SequenceTarget() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Sparse
            return tokenIds.contentEquals(other.tokenIds)
        }

        override fun hashCode(): Int = tokenIds.contentHashCode()
    }
}

/**
 * Interface for generating training sequences from tokenized text data.
 *
 * Sequence generators are responsible for creating input-target pairs from a stream
 * of tokens, which are essential for training language models and other sequence-based
 * neural networks. The generator creates sliding windows over the token sequence,
 * where each window becomes a training example.
 *
 * The typical workflow involves:
 * 1. Taking a list of tokenized integers representing text
 * 2. Creating overlapping sequences of specified length
 * 3. Converting tokens to appropriate numerical representations
 * 4. Pairing input sequences with their corresponding targets
 *
 * This is commonly used in:
 * - Language model training (GPT-style models)
 * - Text generation tasks
 * - Sequence-to-sequence learning
 * - Time series prediction with text data
 *
 * @see DefaultSequenceGenerator
 */
interface SequenceGenerator {
    /**
     * Generates training sequences from a list of tokens using sliding windows.
     *
     * Creates overlapping sequences from the input tokens where each sequence
     * serves as a training example. The method returns pairs where the first
     * element is the input sequence and the second element contains the target
     * sequences (typically the next tokens in the sequence).
     *
     * The sliding window approach allows for efficient use of training data by
     * creating multiple overlapping examples from a single text sequence.
     *
     * @param tokens List of integer tokens representing the tokenized text.
     *               Each integer should correspond to a valid token in the vocabulary.
     * @param seqLength The length of each generated sequence. This determines
     *                  how many tokens are included in each training example.
     * @param stride The step size between consecutive sequences. A stride of 1
     *               creates maximally overlapping sequences, while larger strides
     *               create less overlap and fewer training examples.
     * @return A Sequence of training pairs where:
     *         - First element (FloatArray): Input sequence representation
     *         - Second element (IntArray): Target sequence representations
     *         The Sequence is lazy-evaluated for memory efficiency with large datasets.
     * @throws IllegalArgumentException if seqLength <= 0, stride <= 0, or
     *                                 tokens list is too short for the specified sequence length
     */
    fun generateSequences(
        tokens: List<Int>,
        seqLength: Int,
        stride: Int,
        shuffle: Boolean
    ): Sequence<Pair<FloatArray, IntArray>>
}
