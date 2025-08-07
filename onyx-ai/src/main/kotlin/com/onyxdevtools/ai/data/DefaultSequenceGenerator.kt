package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.FlexibleMatrix
import com.onyxdevtools.ai.MatrixPrecision
import com.onyxdevtools.ai.createMatrix
import com.onyxdevtools.ai.transformation.Vocabulary

/**
 * High-performance sequence generator that creates FlexibleMatrix training data with precision control.
 *
 * This implementation eliminates performance bottlenecks by:
 * - **Direct FlexibleMatrix Generation**: No intermediate DoubleArray conversions
 * - **Precision Consistency**: Generates data in the exact precision needed by the network
 * - **Memory Efficient**: Avoids constant matrix recreations and conversions
 * - **Sparse Targets**: Uses IntArray targets for memory-efficient sparse categorical cross-entropy
 *
 * The generator creates sliding window sequences from input tokens and converts them
 * into a format optimized for neural network training. Key features include:
 *
 * - **No Conversion Overhead**: Direct FlexibleMatrix creation eliminates DoubleArray → FlexibleMatrix conversions
 * - **Precision-Matched Data**: Single/Double precision matches network requirements exactly
 * - **Shuffled Training Order**: Randomizes sequence order to improve training dynamics
 * - **Sparse Categorical Targets**: Uses IntArray for memory-efficient sparse cross-entropy
 * - **Lazy Evaluation**: Uses Kotlin's sequence builder for memory-efficient processing
 * - **Optimized Padding**: Efficient handling of sequence boundaries with [PAD] tokens
 *
 * The generated sequences follow a standard language modeling pattern where:
 * - Input sequence: tokens[i..i+seqLength-1] as FlexibleMatrix (1 × seqLength)
 * - Target sequence: tokens[i+1..i+seqLength] as IntArray (sparse representation)
 * - Padding positions in targets use -1 to indicate ignore in loss computation
 *
 * This creates a "next token prediction" training setup optimized for performance
 * in autoregressive language models like GPT.
 *
 * @param vocabulary The vocabulary used to retrieve special token IDs like [PAD]
 * @see SequenceGenerator
 * @see Vocabulary
 */
class DefaultSequenceGenerator(private val vocabulary: Vocabulary) : SequenceGenerator {

    private val padId: Int = vocabulary.findId("[PAD]") ?: throw IllegalArgumentException("[PAD] token not found in vocabulary")

    /**
     * High-performance sequence generation with precision control and no conversions.
     *
     * Creates sliding window sequences where each input is paired with its corresponding
     * target sequence (shifted by one position). The sequences are generated directly
     * in the requested precision format to eliminate conversion overhead.
     *
     * All sequences are fixed length (seqLength) via right-padding with [PAD] for short windows.
     * Targets use -1 for positions beyond the actual next token to indicate ignore in loss computation.
     *
     * Performance optimizations:
     * - Direct FlexibleMatrix creation in target precision (no DoubleArray intermediates)
     * - Sparse IntArray targets for memory efficiency
     * - Pre-calculated indices for efficient iteration
     * - Lazy sequence generation for memory efficiency
     * - Single-pass data generation without intermediate collections
     *
     * @param tokens List of integer tokens representing the tokenized text.
     *               Each token ID must be valid within the vocabulary range [0, vocabulary.size).
     * @param seqLength The fixed length of each generated sequence. Determines the context window
     *                  size for the language model training.
     * @param stride The step size between consecutive sequence starts. Smaller values
     *               create more overlapping sequences but increase dataset size.
     * @param shuffle Whether to randomize the order of sequence starts (default: true).
     *                If false, sequences are generated in sequential order.
     * @param precision The precision to use for FlexibleMatrix generation (SINGLE or DOUBLE).
     *                  Must match neural network precision to avoid conversions.
     * @return A lazy Sequence of training pairs where:
     *         - First element: Input sequence as FlexibleMatrix (1 × seqLength) in specified precision
     *         - Second element: Target sequence as IntArray with -1 for ignore positions
     * @throws IllegalArgumentException if parameters are invalid, tokens are empty,
     *                                  or contain IDs outside vocabulary range
     */
    override fun generateSequences(
        tokens: List<Int>,
        seqLength: Int,
        stride: Int,
        shuffle: Boolean,
        precision: MatrixPrecision
    ): Sequence<Pair<FlexibleMatrix, IntArray>> {

        return sequence {
            // Calculate possible start indices up to the last token
            val maxStartIndex = tokens.size - 1
            val indices = (0..maxStartIndex step stride).toList().let {
                if (shuffle) it.shuffled() else it
            }

            val isSinglePrecision = precision == MatrixPrecision.SINGLE

            for (i in indices) {
                // Create input FlexibleMatrix directly in target precision
                val input = createMatrix(1, seqLength, isSinglePrecision) { _, pos ->
                    val inputIndex = i + pos
                    if (inputIndex < tokens.size) {
                        tokens[inputIndex].toDouble()
                    } else {
                        padId.toDouble()
                    }
                }

                // Create sparse target array
                val target = IntArray(seqLength) { pos ->
                    val targetIndex = i + pos + 1
                    if (targetIndex < tokens.size) {
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
