package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.transformation.Vocabulary

/**
 * Memory-efficient implementation of SequenceGenerator using sparse target representations.
 *
 * Unlike DefaultSequenceGenerator which creates full one-hot encoded vectors for targets,
 * this implementation stores only the target token IDs. This dramatically reduces memory usage
 * from O(vocab_size × seq_length) to O(seq_length) per training example.
 *
 * Memory comparison for vocab_size=50K, seq_length=512:
 * - DefaultSequenceGenerator: ~200MB per training example
 * - SparseSequenceGenerator: ~4KB per training example
 * 
 * This sparse approach is essential for training with large vocabularies and is compatible
 * with sparse categorical cross-entropy loss functions that expect target token IDs rather
 * than one-hot encoded vectors.
 *
 * Key features:
 * - **Sparse Targets**: Stores target token IDs instead of one-hot vectors
 * - **Ignore Tokens**: Uses -1 to indicate positions that should be ignored in loss computation
 * - **Lazy Evaluation**: Memory-efficient sequence generation
 * - **Shuffled Training**: Randomized sequence order for better training
 * - **Padding Support**: Handles sequences shorter than target length
 *
 * The generated sequences follow the standard language modeling pattern:
 * - Input sequence: tokens[i..i+seqLength-1] as raw token IDs (padded with [PAD] if short)
 * - Target sequence: tokens[i+1..i+seqLength] as sparse token IDs (-1 for ignore/padded positions)
 *
 * @param vocabulary The vocabulary used to retrieve the [PAD] token ID and validate token ranges.
 *                   Must contain a [PAD] token for handling variable-length sequences.
 * @see SequenceGenerator
 * @see Vocabulary
 */
class SparseSequenceGenerator(private val vocabulary: Vocabulary) : SequenceGenerator {

    private val padId: Int = vocabulary.getId("[PAD]") 
        ?: throw IllegalArgumentException("[PAD] token not found in vocabulary")
    
    // Use -1 to indicate positions that should be ignored in loss computation
    private val ignoreId: Int = -1

    /**
     * Generates memory-efficient training sequences with sparse target representations.
     *
     * Creates sliding window sequences where inputs are raw token IDs and targets are
     * sparse token IDs (not one-hot vectors). This reduces memory usage by orders of 
     * magnitude compared to dense representations.
     *
     * Implementation details:
     * - Input sequences contain raw token IDs converted to doubles, padded with [PAD] if needed
     * - Target sequences contain sparse token IDs (-1 for ignore/padded positions)
     * - Sequences are optionally shuffled for better training dynamics
     * - Uses lazy sequence generation for memory efficiency
     * - All possible starting positions are included (even short ones) by padding as needed
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
     *         - Second element: Target sequence as IntArray of sparse token IDs (-1 for ignore/padded)
     * @throws IllegalArgumentException if parameters are invalid, tokens are empty,
     *                                  or contain IDs outside vocabulary range
     */
    override fun generateSequences(
        tokens: List<Int>,
        seqLength: Int,
        stride: Int,
        shuffle: Boolean
    ): Sequence<Pair<FloatArray, IntArray>> {
        // Enhanced validation for robustness
        require(seqLength > 0) { "seqLength must be positive" }
        require(stride > 0) { "stride must be positive" }
        require(tokens.isNotEmpty()) { "tokens list must not be empty" }

        val vocabSize = vocabulary.size
        val minToken = tokens.minOrNull() ?: throw IllegalArgumentException("tokens list is empty")
        val maxToken = tokens.maxOrNull() ?: throw IllegalArgumentException("tokens list is empty")
        require(minToken >= 0 && maxToken < vocabSize) {
            "All token IDs must be in [0, $vocabSize). Found min: $minToken, max: $maxToken"
        }

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

                    // Set input token (padded if beyond sequence)
                    input[pos] = if (inputIndex < tokens.size) {
                        tokens[inputIndex].toFloat()
                    } else {
                        padId.toFloat()
                    }

                    // Set target token (ignore if beyond sequence)
                    target[pos] = if (targetIndex < tokens.size) {
                        tokens[targetIndex]
                    } else {
                        ignoreId  // -1 indicates ignore in loss computation
                    }
                }

                yield(input to target)
            }
        }
    }
}
