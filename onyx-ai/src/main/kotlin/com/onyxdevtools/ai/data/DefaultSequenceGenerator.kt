package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.transformation.Vocabulary

/**
 * Default implementation of SequenceGenerator for creating training sequences from tokenized text.
 *
 * This implementation creates sliding window sequences from input tokens and converts them
 * into a format suitable for neural network training. Key features include:
 *
 * - **Shuffled Training Order**: Randomizes the order of generated sequences to improve training
 * - **One-Hot Encoded Targets**: Converts target tokens to one-hot vectors based on vocabulary size;
 *   uses all-zero vectors for padded/ignored positions to indicate end of actual sequence
 * - **Sliding Window Approach**: Creates overlapping sequences with configurable stride
 * - **Lazy Evaluation**: Uses Kotlin's sequence builder for memory-efficient processing
 * - **Padding for Fixed Length**: Always produces fixed-length sequences by padding short windows
 *   with [PAD] token in inputs; indicates end of actual sequence via all-zero targets
 * - **Next-Token Prediction**: Input sequence predicts shifted targets; ignored positions (all-zero)
 *   should be masked in loss (e.g., where target sum == 0)
 *
 * The generated sequences follow a standard language modeling pattern where:
 * - Input sequence: tokens[i..i+seqLength-1] as raw token IDs (padded with [PAD] if short)
 * - Target sequence: tokens[i+1..i+seqLength] as one-hot encoded vectors (all-zero for ignored/padded)
 *
 * This creates a "next token prediction" training setup commonly used in
 * autoregressive language models like GPT, with handling for end-of-sequence via padding and ignore indicators.
 *
 * @param vocabulary The vocabulary used to determine the size of one-hot encoded target vectors
 *                   and retrieve special token IDs like [PAD]. Must contain all tokens that appear
 *                   in the input token sequences, plus [PAD].
 * @see SequenceGenerator
 * @see Vocabulary
 */
class DefaultSequenceGenerator(private val vocabulary: Vocabulary) : SequenceGenerator {

    private val padId: Int = vocabulary.findId("[PAD]") ?: throw IllegalArgumentException("[PAD] token not found in vocabulary")

    /**
     * Generates training sequences from tokens with optional shuffled order and one-hot encoded targets.
     *
     * Creates sliding window sequences where each input is paired with its corresponding
     * target sequence (shifted by one position). The sequences are generated in random
     * order (if shuffle is true) to improve training convergence and prevent overfitting to data ordering.
     *
     * All sequences are fixed length (seqLength) via right-padding with [PAD] for short windows.
     * Targets use all-zero vectors for positions beyond the actual next token to indicate ignore in loss computation.
     *
     * Implementation details:
     * - Input sequences contain raw token IDs converted to doubles, padded with [PAD] if needed
     * - Target sequences are one-hot encoded (or all-zero for ignore); use categorical cross-entropy with masking in training
     * - Sequences are optionally shuffled for better training dynamics
     * - Uses lazy sequence generation for memory efficiency
     * - Includes all possible starting positions (even short ones) by padding as needed
     *
     * @param tokens List of integer tokens representing the tokenized text.
     *               Each token ID must be valid within the vocabulary range [0, vocabulary.size).
     * @param seqLength The fixed length of each generated sequence. Determines the context window
     *                  size for the language model training.
     * @param stride The step size between consecutive sequence starts. Smaller values
     *               create more overlapping sequences but increase dataset size.
     * @param shuffle Whether to randomize the order of sequence starts (default: true).
     *                If false, sequences are generated in sequential order.
     * @return A lazy Sequence of training pairs where:
     *         - First element: Input sequence as FloatArray of token IDs (padded if needed)
     *         - Second element: Target sequences as Array<FloatArray> of one-hot vectors (all-zero for ignore/padded)
     *         Each target vector has size equal to vocabulary.size.
     * @throws IllegalArgumentException if parameters are invalid, tokens are empty,
     *                                  or contain IDs outside vocabulary range
     */
    override fun generateSequences(
        tokens: List<Int>,
        seqLength: Int,
        stride: Int,
        shuffle: Boolean
    ): Sequence<Pair<FloatArray, IntArray>> {

        return sequence {
            // Calculate possible start indices up to the last token
            val maxStartIndex = tokens.size - 1
            val indices = (0..maxStartIndex step stride).toList().let {
                if (shuffle) it.shuffled() else it
            }

            for (i in indices) {
                val input = FloatArray(seqLength)
                val target = IntArray(seqLength)

                for (pos in 0 until seqLength) {
                    val inputIndex = i + pos
                    val targetIndex = i + pos + 1

                    input[pos] = if (inputIndex < tokens.size) {
                        tokens[inputIndex].toFloat()
                    } else {
                        padId.toFloat()
                    }

                    target[pos] = if (targetIndex < tokens.size) {
                        tokens[targetIndex]
                    } else {
                        -1  // Use -1 to indicate ignore in loss computation
                    }
                }

                yield(input to target)
            }
        }
    }
}
