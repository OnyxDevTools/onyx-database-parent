package com.onyxdevtools.ai.generation

import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary

interface TextGenerator {
    fun generate(
        model: NeuralNetwork,
        tokenizer: BPETokenizer,
        vocabulary: Vocabulary,
        prompt: String,
        maxGenerate: Int,
        seqLength: Int
    ): String
}
