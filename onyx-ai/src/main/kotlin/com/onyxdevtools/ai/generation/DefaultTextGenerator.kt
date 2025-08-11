package com.onyxdevtools.ai.generation

import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary

// DefaultTextGenerator changes
class DefaultTextGenerator : TextGenerator {

    private fun softmaxTemp(logits: FloatArray, temperature: Float): FloatArray {
        val t = temperature.coerceAtLeast(1e-6f)
        val maxLogit = logits.maxOrNull() ?: 0.0f
        val exps = FloatArray(logits.size) { kotlin.math.exp((logits[it] - maxLogit) / t) }
        val sum = exps.sum().coerceAtLeast(1e-12f)
        return FloatArray(exps.size) { exps[it] / sum }
    }

    private fun applyRepetitionPenalty(logits: FloatArray, seenCounts: IntArray, penalty: Float) {
        if (penalty <= 1.0) return
        for (i in logits.indices) {
            if (seenCounts[i] > 0) {
                logits[i] = if (logits[i] > 0) logits[i] / penalty else logits[i] * penalty
            }
        }
    }


    private fun sampleTopPTopK(probs: FloatArray, topP: Float, topK: Int): Int {
        // mask invalid (<0) probs
        val pairs = probs.withIndex().filter { it.value > 0.0f }.sortedByDescending { it.value }
        val truncated = if (topK > 0) pairs.take(topK) else pairs
        var cum = 0.0f
        val nucleus = if (topP in 0.0f..0.999f) {
            val lst = mutableListOf<IndexedValue<Float>>()
            for (p in truncated) {
                cum += p.value
                lst += p
                if (cum >= topP) break
            }
            if (lst.isEmpty()) truncated.take(1) else lst
        } else truncated

        val sum = nucleus.sumOf { it.value.toDouble() }.toFloat()
        val r = kotlin.random.Random.Default.nextFloat() * sum
        var acc = 0.0f
        for (p in nucleus) {
            acc += p.value
            if (r <= acc) return p.index
        }
        return nucleus.last().index
    }

    private fun blockNoRepeatBigram(candidatesMask: BooleanArray, generated: List<Int>) {
        if (generated.size < 2) return
        // Build a small set of seen bigrams (u -> v)
        val seen = HashSet<Pair<Int, Int>>()
        for (i in 0 until generated.size - 1) {
            seen.add(Pair(generated[i], generated[i + 1]))
        }
        val last = generated.last()
        // Block any v where (last, v) was seen
        for (v in candidatesMask.indices) {
            if (seen.contains(Pair(last, v))) candidatesMask[v] = false
        }
    }

    override fun generate(
        model: NeuralNetwork,
        tokenizer: BPETokenizer,
        vocabulary: Vocabulary,
        prompt: String,
        maxGenerate: Int,
        seqLength: Int
    ): String {
        // --- IDs ----
        fun id(tok: String) = vocabulary.getId(tok)
        val padId = id("[PAD]")
        val clsId = id("[CLS]")
        val unkId = id("[UNK]")
        val maskId = id("[MASK]")
        val sotId = id("[SOT]")
        val sepId = id("[SEP]")
        val eotId = id("[EOT]") // whichever you trained

        // tokens we NEVER want to sample (exclude eos from this set!)
        val neverSample = buildSet<Int> {
            listOf(padId, clsId, unkId, maskId, sotId, sepId).forEach { if (it != null) add(it) }
            // If you want to be extra strict, you can also add byte tokens by range if your vocab uses them contiguously.
        }

        // --- sampling hyperparams ---
        val temperature = 0.9f
        val topP = 0.9f
        val topK = 40
        val repetitionPenalty = 1.1f
        val useNoRepeatBigram = true

        // *** use CAUSAL encoding ***
        val ids = tokenizer.encodeCausal(prompt).toMutableList()

        // repetition bookkeeping
        val seenCounts = IntArray(vocabulary.size) { 0 }
        ids.forEach { if (it in seenCounts.indices) seenCounts[it]++ }

        repeat(maxGenerate) {
            val current = if (ids.size >= seqLength) ids.takeLast(seqLength) else {
                val pad = padId ?: 0
                List(seqLength - ids.size) { pad } + ids
            }
            val input = current.map { it.toFloat() }.toFloatArray()
            val predictions = model.predict(arrayOf(input))

            // If your model returns [timeSteps x vocab], last step is usually the one we want.
            val nextPosition = predictions.lastIndex.coerceAtLeast(0)
            val logits = predictions[nextPosition].copyOf()

            // mask specials
            neverSample.forEach { bad ->
                if (bad in logits.indices) logits[bad] = Float.NEGATIVE_INFINITY
            }

            // repetition penalty
            applyRepetitionPenalty(logits, seenCounts, repetitionPenalty)

            // temperature -> probs
            var probs = softmaxTemp(logits, temperature)

            // no-repeat-bigram
            if (useNoRepeatBigram) {
                val mask = BooleanArray(probs.size) { true }
                blockNoRepeatBigram(mask, ids)
                for (i in probs.indices) if (!mask[i]) probs[i] = 0.0f
            }

            // renormalize
            val s = probs.sum().coerceAtLeast(1e-12f)
            for (i in probs.indices) probs[i] /= s

            val nextId = sampleTopPTopK(probs, topP, topK)
            ids += nextId
            if (nextId in seenCounts.indices) seenCounts[nextId]++

            if (eotId != null && nextId == eotId) return tokenizer.decode(ids)
        }

        return tokenizer.decode(ids)
    }
}

fun NeuralNetwork.chat(prompt: String, vocabulary: Vocabulary, seqLength: Int = 256, maxTokens: Int = seqLength) : String{
    val tokenizer = BPETokenizer(vocabulary)
    val textGenerator: TextGenerator = DefaultTextGenerator()
    return textGenerator.generate(this, tokenizer, vocabulary, prompt, maxTokens, seqLength)
}
