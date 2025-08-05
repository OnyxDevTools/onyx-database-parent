package com.onyxdevtools.ai.generation

import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary
import kotlin.math.min

/**
 * Default implementation of the TextGenerator interface.
 * 
 * This generator uses a trained neural network model to generate text autoregressively,
 * producing one token at a time based on the input prompt and previously generated tokens.
 * It uses greedy decoding (selecting the most probable next token at each step).
 */
class DefaultTextGenerator : TextGenerator {
    
    /**
     * Generates text using the provided model and tokenizer.
     * 
     * This method performs autoregressive text generation by:
     * 1. Tokenizing the input prompt
     * 2. Iteratively predicting the next token using the model
     * 3. Appending each predicted token to the sequence
     * 4. Converting the final token sequence back to text
     *
     * @param model The trained neural network model to use for generation
     * @param tokenizer The BPE tokenizer for text preprocessing
     * @param vocabulary The vocabulary mapping tokens to IDs
     * @param prompt The initial text prompt to start generation from
     * @param maxGenerate Maximum number of tokens to generate
     * @param seqLength Maximum sequence length the model can process
     * @return The generated text as a string
     */
    override fun generate(
        model: NeuralNetwork,
        tokenizer: BPETokenizer,
        vocabulary: Vocabulary,
        prompt: String,
        maxGenerate: Int,
        seqLength: Int
    ): String {
        val padId = vocabulary.getId("[PAD]")
        val promptTokens = tokenizer.tokenize(prompt)
            .map { vocabulary.getId(it) }
            .toMutableList()
        
        // Generate tokens autoregressively
        for (i in 0 until maxGenerate) {
            val currentLength = promptTokens.size
            
            // Prepare input sequence with appropriate padding/truncation
            val inputList = if (currentLength >= seqLength) {
                promptTokens.takeLast(seqLength)
            } else {
                List(seqLength - currentLength) { padId } + promptTokens
            }
            
            // Convert to model input format
            val input = inputList.map { it.toDouble() }.toDoubleArray()
            val predictions = model.predict(arrayOf(input))
            
            // Get logits for the next token position
            val nextPosition = min(currentLength, seqLength) - 1
            val logits = predictions[nextPosition]
            
            // Greedy decoding: select token with highest probability
            val predictedId = logits.indices.maxByOrNull { logits[it] }!!
            promptTokens.add(predictedId)
        }
        
        // Convert tokens back to text
        return promptTokens
            .map { vocabulary.getToken(it) ?: "[UNK]" }
            .joinToString(" ")
    }
}
