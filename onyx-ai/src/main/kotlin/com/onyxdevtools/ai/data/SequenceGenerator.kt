package com.onyxdevtools.ai.data

import com.onyxdevtools.ai.transformation.Vocabulary

interface SequenceGenerator {
    fun generateSequences(tokens: List<Int>, seqLength: Int, stride: Int): Sequence<Pair<DoubleArray, Array<DoubleArray>>>
}
