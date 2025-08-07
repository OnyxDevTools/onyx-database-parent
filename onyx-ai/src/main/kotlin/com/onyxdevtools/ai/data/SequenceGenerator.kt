package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.FlexibleMatrix
import com.onyxdevtools.ai.MatrixPrecision

/**
 * Interface for generating training sequences from tokenized text data with precision control.
 *
 * Sequence generators transform raw token sequences into structured training data
 * suitable for neural network training, particularly for language modeling tasks.
 * They handle the conversion from discrete tokens to appropriate input/output pairs
 * while managing sequence length, padding, and target encoding requirements.
 *
 * Key features:
 * - **Precision Control**: Generate data in SINGLE or DOUBLE precision to match network requirements
 * - **Memory Efficient**: Avoid constant conversions between precision formats
 * - **FlexibleMatrix Integration**: Direct integration with FlexibleMatrix for optimal performance
 * - **Lazy Evaluation**: Memory-efficient processing for large datasets
 *
 * This abstraction allows for different sequence generation strategies:
 * - Dense one-hot encoded targets for categorical cross-entropy
 * - Sparse integer targets for memory-efficient training
 * - Custom sequence windowing and overlap patterns
 * - Different padding and truncation strategies
 *
 * Implementations should be stateless and thread-safe to support concurrent
 * data loading and training scenarios.
 *
 * @see DefaultSequenceGenerator
 * @see SparseSequenceGenerator
 */
interface SequenceGenerator {

    /**
     * Generates training sequences from a list of tokens with precision control.
     *
     * This method creates sliding window sequences of fixed length from the input tokens,
     * where each sequence serves as input and the corresponding shifted sequence serves as target.
     * The precision parameter ensures data matches the neural network's precision requirements.
     *
     * @param tokens List of integer tokens representing the tokenized text.
     *               Token IDs should be within the vocabulary range supported by the implementation.
     * @param seqLength The desired length of each generated sequence.
     *                  Determines the context window size for the model.
     * @param stride The step size between consecutive sequence starts.
     *               Smaller values create more overlapping sequences but increase dataset size.
     * @param shuffle Whether to randomize the order of generated sequences (default: true).
     *                Randomization improves training convergence and prevents overfitting to data ordering.
     * @param precision The precision to use for generated data (SINGLE or DOUBLE).
     *                  Must match the neural network's precision to avoid conversions.
     * @return A lazy [Sequence] of training pairs where:
     *         - First: Input sequence as FlexibleMatrix (batchSize=1, cols=seqLength)
     *         - Second: Target tokens as IntArray for sparse categorical cross-entropy
     * @throws IllegalArgumentException if parameters are invalid or tokens contain unsupported values.
     */
    fun generateSequences(
        tokens: List<Int>,
        seqLength: Int,
        stride: Int = 1,
        shuffle: Boolean = true,
        precision: MatrixPrecision = MatrixPrecision.SINGLE
    ): Sequence<Pair<FlexibleMatrix, IntArray>>
}
