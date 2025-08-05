package com.onyxdevtools.ai.transformation

class MutableVocabulary : Vocabulary {
    private val tokenToId = mutableMapOf<String, Int>()
    private val idToToken = mutableMapOf<Int, String>()

    override fun getId(token: String): Int {
        return tokenToId.getOrPut(token) {
            val id = tokenToId.size
            idToToken[id] = token
            id
        }
    }

    override fun getToken(id: Int): String? = idToToken[id]

    override fun findId(token: String): Int? = tokenToId[token]

    override val size: Int get() = tokenToId.size

    override fun addToken(token: String) {
        getId(token) // Adds if not present
    }
}
