package dev.onyx.ai.generation

import dev.onyx.ai.NeuralNetwork
import dev.onyx.ai.transformation.BPETokenizer
import dev.onyx.ai.transformation.Vocabulary

/**
 * Interface for generating text using trained neural network models.
 *
 * Text generators coordinate the process of producing new text content by:
 * 1. Tokenizing input prompts
 * 2. Feeding token sequences through neural networks
 * 3. Sampling from output probability distributions
 * 4. Converting generated tokens back to human-readable text
 *
 * The generation process typically follows an autoregressive pattern where
 * each new token is generated based on the previous context, allowing for
 * coherent and contextually appropriate text production.
 *
 * Common applications include:
 * - Language model inference (GPT-style text completion)
 * - Creative writing assistance
 * - Code generation
 * - Question answering systems
 * - Conversational AI
 *
 * Different implementations may employ various sampling strategies such as:
 * - Greedy decoding (selecting highest probability tokens)
 * - Top-k sampling (sampling from k most likely tokens)
 * - Nucleus/top-p sampling (sampling from cumulative probability mass)
 * - Temperature-based sampling (adjusting probability distributions)
 *
 * @see DefaultTextGenerator
 * @see NeuralNetwork
 * @see BPETokenizer
 * @see Vocabulary
 */
interface TextGenerator {
    /**
     * Generates new text based on a given prompt using a trained neural network model.
     *
     * The generation process involves multiple steps:
     * 1. Tokenize the input prompt using the provided tokenizer
     * 2. Create initial context from the tokenized prompt
     * 3. Iteratively generate new tokens by feeding context through the model
     * 4. Sample next tokens from the model's output probability distribution
     * 5. Update context with generated tokens for subsequent predictions
     * 6. Continue until maxGenerate tokens are produced or an end condition is met
     * 7. Detokenize the generated sequence back to readable text
     *
     * The quality and style of generated text depends on:
     * - Model architecture and training quality
     * - Prompt engineering and context setup
     * - Sampling strategy and parameters
     * - Vocabulary coverage and tokenization quality
     *
     * @param model The trained neural network model used for generating predictions.
     *              Should be a language model trained on text data with appropriate
     *              architecture (e.g., transformer-based).
     * @param tokenizer The BPE tokenizer for converting between text and token sequences.
     *                  Must be the same tokenizer used during model training.
     * @param vocabulary The vocabulary containing all valid tokens the model can produce.
     *                   Must match the vocabulary used during training.
     * @param prompt The input text prompt to condition generation on. This provides
     *               the initial context for the generation process.
     * @param maxGenerate Maximum number of new tokens to generate. The actual output
     *                    may be shorter if end-of-sequence conditions are met.
     * @param seqLength The sequence length/context window size used by the model.
     *                  Should match the sequence length used during training.
     * @return The generated text as a String, combining the original prompt context
     *         with the newly generated content.
     * @throws IllegalArgumentException if parameters are invalid (e.g., negative maxGenerate)
     * @throws IllegalStateException if the model, tokenizer, or vocabulary are incompatible
     */
    fun generate(
        model: NeuralNetwork,
        tokenizer: BPETokenizer,
        vocabulary: Vocabulary,
        prompt: String,
        maxGenerate: Int,
        seqLength: Int
    ): String
}

