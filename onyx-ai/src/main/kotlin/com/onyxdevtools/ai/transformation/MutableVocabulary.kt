package com.onyxdevtools.ai.transformation

class MutableVocabulary : Vocabulary {
    /** Map from token strings to their assigned integer IDs */
    private val tokenToId = mutableMapOf<String, Int>()
    /** Map from integer IDs back to their corresponding token strings */
    private val idToToken = mutableMapOf<Int, String>()
    /** Frequency counter used by commit() */
    private val freq = mutableMapOf<String, Long>()

    override fun getId(token: String): Int {
        val id = tokenToId.getOrPut(token) {
            val newId = tokenToId.size
            idToToken[newId] = token
            newId
        }
        // keep freq map in sync
        freq.putIfAbsent(token, 0L)
        return id
    }

    override fun getToken(id: Int): String? = idToToken[id]

    override fun findId(token: String): Int? = tokenToId[token]

    override val size: Int get() = tokenToId.size

    override fun addToken(token: String) {
        // Adds if not present and ensures freq initialized
        if (token !in tokenToId) {
            val id = tokenToId.size
            tokenToId[token] = id
            idToToken[id] = token
        }
        freq.putIfAbsent(token, 0L)
    }

    /** Increment token frequency; creates token if missing. */
    fun incrementFrequency(token: String, amount: Long = 1) {
        addToken(token)
        freq[token] = (freq[token] ?: 0L) + amount
    }

    /** Helper to fetch tokenizer defaults if available, otherwise empty. */
    private fun mustKeepTokens(): Set<String> =
        runCatching { BPETokenizer(this).defaultTokens.toSet() }.getOrDefault(emptySet())

    /**
     * Commit the vocabulary in-memory:
     *  - Keep only the top [maxTokens] by frequency (desc), tie-break by token (asc).
     *  - Always include tokenizer default tokens (if defined) even if not in top-N.
     *  - Reassign dense IDs starting at 0 in the final rank order.
     */
    override fun commit(maxTokens: Int) {
        require(maxTokens > 0) { "maxTokens must be > 0" }

        val mustKeep = mustKeepTokens()

        // Seed them so they exist (freq defaults to 0)
        mustKeep.forEach { addToken(it) }

        // Snapshot all tokens with freq (default 0)
        val all = tokenToId.keys.map { t -> t to (freq[t] ?: 0L) }

        // Rank by (freq desc, token asc)
        val ranked = all.sortedWith(
            compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
        )

        // Take top-N
        val top = ranked.take(maxTokens).map { it.first }.toMutableList()

        // Ensure must-keep tokens are included
        for (t in mustKeep) if (t !in top) top += t

        // If we exceeded maxTokens, trim NON-mustkeep, keeping all must-keep
        if (top.size > maxTokens) {
            val mustKeepSet = mustKeep.toSet()
            val keepMust = top.filter { it in mustKeepSet }
            val keepNon  = top.filter { it !in mustKeepSet }
            val room = (maxTokens - keepMust.size).coerceAtLeast(0)
            val trimmed = keepMust + keepNon.take(room)
            top.clear()
            top += trimmed
        }

        // Reassign dense IDs starting at 0
        tokenToId.clear()
        idToToken.clear()

        val newFreq = mutableMapOf<String, Long>()
        top.forEachIndexed { newId, token ->
            tokenToId[token] = newId
            idToToken[newId] = token
            newFreq[token] = freq[token] ?: 0L
        }
        freq.clear()
        freq.putAll(newFreq)
    }
}
