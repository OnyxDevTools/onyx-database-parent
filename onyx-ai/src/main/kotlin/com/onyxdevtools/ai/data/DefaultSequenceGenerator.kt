package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.transformation.Vocabulary

class DefaultSequenceGenerator(private val vocabulary: Vocabulary) : SequenceGenerator {
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
