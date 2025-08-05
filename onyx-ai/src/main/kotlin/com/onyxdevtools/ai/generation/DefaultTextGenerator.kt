package com.onyxdevtools.ai.generation

import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary
import kotlin.math.min

class DefaultTextGenerator : TextGenerator {
    override fun generate(
        model: NeuralNetwork,
        tokenizer: BPETokenizer,
        vocabulary: Vocabulary,
        prompt: String,
        maxGenerate: Int,
        seqLength: Int
    ): String {
        val padId = vocabulary.getId("[PAD]")
        val promptTokens = tokenizer.tokenize(prompt).map { vocabulary.getId(it) }.toMutableList()
        for (i in 0 until maxGenerate) {
            val currentLength = promptTokens.size
            val inputList = if (currentLength >= seqLength) {
                promptTokens.takeLast(seqLength)
            } else {
                List(seqLength - currentLength) { padId } + promptTokens
            }
            val input = inputList.map { it.toDouble() }.toDoubleArray()
            val predictions = model.predict(arrayOf(input))
            val nextPosition = min(currentLength, seqLength) - 1
            val logits = predictions[nextPosition]
            val predictedId = logits.indices.maxByOrNull { logits[it] }!!
            promptTokens.add(predictedId)
        }
        return promptTokens.map { vocabulary.getToken(it) ?: "[UNK]" }.joinToString(" ")
    }
}
