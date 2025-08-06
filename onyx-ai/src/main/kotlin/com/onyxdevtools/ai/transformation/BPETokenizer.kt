package com.onyxdevtools.ai.transformation

import java.text.Normalizer

/**
 * Byte-Pair Encoding (BPE) Tokenizer implementation.
 * 
 * This tokenizer uses BPE subword encoding to handle out-of-vocabulary words
 * and supports various text preprocessing options including normalization,
 * case sensitivity, and diacritics removal.
 *
 * @param vocabulary The vocabulary to use for tokenization
 * @param unkToken Token to use for unknown words (default: "[UNK]")
 * @param maxInputCharsPerWord Maximum characters per word before using UNK token
 * @param caseSensitive Whether tokenization should be case-sensitive
 * @param normalize Whether to apply Unicode normalization
 * @param removeDiacritics Whether to remove diacritical marks
 * @param customKeywords Custom set of keywords to recognize as tokens
 */
class BPETokenizer(
    private val vocabulary: Vocabulary,
    private val unkToken: String = "[UNK]",
    private val maxInputCharsPerWord: Int = 100,
    private val caseSensitive: Boolean = true,
    private val normalize: Boolean = false,
    private val removeDiacritics: Boolean = false,
    private val customKeywords: Set<String> = emptySet()
) : Tokenizer {

    private val defaultPunctuation: Set<String> by lazy {
        val ascii = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~"""
        buildSet {
            ascii.forEach { add(it.toString()) }
            addAll(listOf("«","»","‹","›","‘","’","‚","‛","“","”","„","‟"))
            addAll(listOf("–","—","-","−"))
            addAll(listOf("…", "\n", "\r", "\n\r", "\r\n"))
        }
    }

    // Add inside BPETokenizer
    val defaultTokens: Set<String>
        get() {
            val punctuation = defaultPunctuation
            val baseKeywords = if (customKeywords.isNotEmpty()) customKeywords else keywords
            return buildSet {
                addAll(specialTokens)
                add(unkToken)
                addAll(baseKeywords)
                addAll(operators)
                addAll(mathSymbols)
                addAll(punctuation)
            }
        }

    private val specialTokens = setOf(
        "[PAD]", "[CLS]", "[SEP]", "[UNK]", "[MASK]",
        "<|startoftext|>", "<|endoftext|>", "<|sep|>"
    )

    private val languageKeywords = mapOf(
        "kotlin" to setOf(
            "if", "else", "fun", "class", "val", "var", "when", "try", "catch", "for", "while",
            "return", "break", "continue", "import", "package", "object", "interface", "typealias",
            "true", "false", "null", "this", "super", "is", "as", "in", "throw", "open", "internal",
            "private", "protected", "public", "override", "abstract", "final", "enum", "companion",
            "data", "sealed", "inline", "noinline", "crossinline", "reified", "const", "lateinit",
            "operator", "infix", "suspend", "actual", "expect", "annotation", "by", "get", "set"
        ),
        "java" to setOf(
            "if", "else", "public", "private", "class", "int", "String", "try", "catch", "for", "while",
            "return", "break", "continue", "import", "package", "new", "this", "super", "void", "boolean",
            "byte", "short", "long", "float", "double", "char", "true", "false", "null", "instanceof",
            "extends", "implements", "interface", "abstract", "final", "static", "synchronized", "volatile",
            "throw", "throws", "transient", "enum", "assert", "switch", "case", "default", "goto", "const",
            "do", "native", "strictfp"
        ),
        "typescript" to setOf(
            "if", "else", "function", "class", "let", "const", "try", "catch", "for", "while",
            "return", "break", "continue", "import", "export", "from", "as", "type", "interface",
            "true", "false", "null", "undefined", "this", "super", "new", "instanceof", "in",
            "typeof", "void", "with", "yield", "async", "await", "extends", "implements",
            "private", "protected", "public", "static", "abstract", "enum", "switch", "case", "default",
            "declare", "module", "namespace", "readonly", "never", "any", "unknown", "keyof"
        ),
        "swift" to setOf(
            "if", "else", "func", "class", "let", "var", "try", "catch", "for", "while",
            "return", "break", "continue", "import", "struct", "enum", "protocol", "extension",
            "true", "false", "nil", "self", "super", "init", "deinit", "subscript", "typealias",
            "associatedtype", "inout", "private", "fileprivate", "internal", "public", "static",
            "final", "open", "dynamic", "lazy", "optional", "required", "convenience", "weak",
            "unowned", "guard", "defer", "repeat", "where", "as", "is", "throw", "throws", "rethrows",
            "precedencegroup", "associativity", "higherThan", "lowerThan", "assignment", "switch",
            "case", "default", "fallthrough"
        ),
        "javascript" to setOf(
            "if", "else", "function", "class", "let", "const", "try", "catch", "for", "while",
            "return", "break", "continue", "import", "export", "from", "as", "true", "false",
            "null", "undefined", "this", "super", "new", "instanceof", "in", "typeof", "void",
            "with", "yield", "async", "await", "switch", "case", "default", "var", "delete",
            "do", "debugger"
        )
    )

    private val keywords = if (customKeywords.isNotEmpty()) customKeywords else languageKeywords.values.flatten().toSet()

    private val operators = listOf(
        "===", "!==", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=",
        "<<", ">>", ">>>", "&", "|", "^", "~", "!", "=", "+", "-", "*", "/", "%", "<", ">",
        ".", "?.", "??", ":", "=>", "->", "?:", "::", "@", "#", "$", "!!", "..", "...", "..<",
        "**", "**=", "==>", "<==", "->>", "<<-", "&&=", "||=", "^^", "??=", "?.[", "?->"
    ).sortedByDescending { it.length }

    private val mathSymbols = setOf(
        "\u222B", "\u2211", "\u220F", "\u221A", "\u221E", "\u2248", "\u2260", "\u2264", "\u2265", "\u00B1", "\u00D7", "\u00F7", "\u221D", "\u2220", "\u2229", "\u222A", "\u2282", "\u2283", "\u2208", "\u2209", "\u2200", "\u2203", "\u2205", "\u2207", "\u2202",
        "\u03B1", "\u03B2", "\u03B3", "\u03B4", "\u03B5", "\u03B6", "\u03B7", "\u03B8", "\u03B9", "\u03BA", "\u03BB", "\u03BC", "\u03BD", "\u03BE", "\u03BF", "\u03C0", "\u03C1", "\u03C3", "\u03C4", "\u03C5", "\u03C6", "\u03C7", "\u03C8", "\u03C9",
        "\u2115", "\u2124", "\u211A", "\u211D", "\u2102"
    )

    private val tokenPattern = Regex(
        "(\\s+)|" +  // Group 1: whitespace
            "(\\b(?:${keywords.joinToString("|")})\\b)|" +  // Group 2: keywords
            "(${operators.joinToString("|") { Regex.escape(it) }})|" +  // Group 3: operators
            "(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)|" +  // Group 4: identifiers
            "(\\b\\d+\\b)|" +  // Group 5: numbers
            "([\\[\\](){}.,;:!?])|" +  // Group 6: punctuation
            "(${mathSymbols.joinToString("|") { Regex.escape(it) }})|" +  // Group 7: math symbols
            "(\\d+/\\d+)"  // Group 8: fractions
    )

    override fun tokenize(text: String): List<String> {
        try {
            val normalizedText = if (normalize) {
                var temp = Normalizer.normalize(text, Normalizer.Form.NFD)
                if (removeDiacritics) temp = temp.replace(Regex("\\p{M}"), "")
                if (!caseSensitive) temp = temp.lowercase()
                temp
            } else {
                text
            }

            val tokens = mutableListOf<String>()
            var lastEnd = 0

            for (match in tokenPattern.findAll(normalizedText)) {
                val start = match.range.first
                if (start > lastEnd) {
                    val unknownText = normalizedText.substring(lastEnd until start)
                    tokens.addAll(bpe(unknownText))
                }

                val groupValues = match.groupValues
                when {
                    groupValues[1].isNotEmpty() -> {} // Whitespace: ignore
                    groupValues[2].isNotEmpty() -> tokens.add(groupValues[2]) // Keyword
                    groupValues[3].isNotEmpty() -> tokens.add(groupValues[3]) // Operator
                    groupValues[4].isNotEmpty() -> tokens.addAll(bpe(groupValues[4])) // Identifier
                    groupValues[5].isNotEmpty() -> tokens.add(groupValues[5]) // Number
                    groupValues[6].isNotEmpty() -> tokens.add(groupValues[6]) // Punctuation
                    groupValues[7].isNotEmpty() -> tokens.add("<|math_${groupValues[7]}|>") // Math symbol
                    groupValues[8].isNotEmpty() -> {
                        val fractionMatch = Regex("(\\d+)/(\\d+)").matchEntire(groupValues[8])
                        if (fractionMatch != null) {
                            val (num, den) = fractionMatch.destructured
                            tokens.add("<|math_frac_${num}_${den}|>")
                        } else {
                            tokens.add(groupValues[8])
                        }
                    }
                }
                lastEnd = match.range.last + 1
            }

            if (lastEnd < normalizedText.length) {
                val unknownText = normalizedText.substring(lastEnd)
                tokens.addAll(bpe(unknownText))
            }

            return tokens
        } catch (e: Exception) {
            println("Error during tokenization: ${e.message}")
            return listOf(unkToken)
        }
    }

    fun bpe(word: String): List<String> {
        if (word.length > maxInputCharsPerWord) return listOf(unkToken)
        if (specialTokens.contains(word)) return listOf(word)

        var chars = word.map { it.toString() }.toMutableList()
        val tokens = mutableListOf<String>()

        while (chars.isNotEmpty()) {
            var token = chars.joinToString("")
            var bestId: Int? = vocabulary.findId(token)

            if (bestId != null) {
                tokens.add(token)
                break
            } else {
                var bestSubstr: String? = null
                for (i in chars.size - 1 downTo 1) {
                    val substr = chars.subList(0, i).joinToString("")
                    if (vocabulary.findId(substr) != null) {
                        bestSubstr = substr
                        break
                    }
                }
                if (bestSubstr != null) {
                    tokens.add(bestSubstr)
                    chars = chars.subList(bestSubstr.length, chars.size)
                } else {
                    tokens.add(unkToken)
                    break
                }
            }
        }
        return tokens
    }

    override fun encode(text: String): List<Int> {
        val tokens = tokenize(text)
        val tokenIds = mutableListOf<Int>()
        tokenIds.add(vocabulary.getId("[CLS]") ?: vocabulary.getId(unkToken)!!)
        tokenIds.addAll(tokens.map { vocabulary.getId(it) ?: vocabulary.getId(unkToken)!! })
        tokenIds.add(vocabulary.getId("[SEP]") ?: vocabulary.getId(unkToken)!!)
        return tokenIds
    }

    override fun encode(text: String, textPair: String): List<Int> {
        val tokens1 = tokenize(text)
        val tokens2 = tokenize(textPair)
        val allTokens = listOf("[CLS]") + tokens1 + listOf("[SEP]") + tokens2 + listOf("[SEP]")
        return allTokens.map { vocabulary.getId(it) ?: vocabulary.getId(unkToken)!! }
    }

    override fun decode(ids: List<Int>): String {
        val tokens = ids.mapNotNull { vocabulary.getToken(it) }
        val text = StringBuilder()
        for (token in tokens) {
            if (token.startsWith("##")) {
                text.append(token.substring(2))
            } else if (!specialTokens.contains(token)) {
                text.append(" ").append(token)
            }
        }
        return text.toString().trim()
    }

    /**
     * Extracts the frequency of each unique token that would be generated from the given text.
     * This method is useful for building vocabularies from large text corpora with frequency information.
     *
     * @param text The input text to extract token frequencies from
     * @return A map of tokens to their occurrence counts in the pre-tokenized text
     */
    fun extractTokenFrequencies(text: String): Map<String, Int> {
        val tokenCounts = mutableMapOf<String, Int>()

        try {
            val normalizedText = if (normalize) {
                var temp = Normalizer.normalize(text, Normalizer.Form.NFD)
                if (removeDiacritics) temp = temp.replace(Regex("\\p{M}"), "")
                if (!caseSensitive) temp = temp.lowercase()
                temp
            } else {
                text
            }

            var lastEnd = 0

            for (match in tokenPattern.findAll(normalizedText)) {
                val start = match.range.first
                if (start > lastEnd) {
                    val unknownText = normalizedText.substring(lastEnd until start)
                    unknownText.forEach { char ->
                        val str = char.toString()
                        tokenCounts[str] = tokenCounts.getOrDefault(str, 0) + 1
                    }
                }

                val groupValues = match.groupValues
                when {
                    groupValues[1].isNotEmpty() -> {} // Whitespace: ignore
                    groupValues[2].isNotEmpty() -> {
                        val token = groupValues[2] // Keyword
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                    groupValues[3].isNotEmpty() -> {
                        val token = groupValues[3] // Operator
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                    groupValues[4].isNotEmpty() -> {
                        val token = groupValues[4] // Identifier
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                    groupValues[5].isNotEmpty() -> {
                        val token = groupValues[5] // Number
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                    groupValues[6].isNotEmpty() -> {
                        val token = groupValues[6] // Punctuation
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                    groupValues[7].isNotEmpty() -> {
                        val token = "<|math_${groupValues[7]}|>" // Math symbol
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                    groupValues[8].isNotEmpty() -> {
                        val fractionMatch = Regex("(\\d+)/(\\d+)").matchEntire(groupValues[8])
                        val token = if (fractionMatch != null) {
                            val (num, den) = fractionMatch.destructured
                            "<|math_frac_${num}_${den}|>"
                        } else {
                            groupValues[8]
                        }
                        tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                    }
                }
                lastEnd = match.range.last + 1
            }

            if (lastEnd < normalizedText.length) {
                val unknownText = normalizedText.substring(lastEnd)
                unknownText.forEach { char ->
                    val str = char.toString()
                    tokenCounts[str] = tokenCounts.getOrDefault(str, 0) + 1
                }
            }
        } catch (e: Exception) {
            println("Error during token frequency extraction: ${e.message}")
        }

        return tokenCounts
    }

    /**
     * Extracts all unique tokens that would be generated from the given text.
     * This method is useful for building vocabularies from large text corpora.
     *
     * @param text The input text to extract tokens from
     * @return A set of unique tokens that would be generated by tokenizing the text
     */
    fun extractUniqueTokens(text: String): Set<String> = extractTokenFrequencies(text).keys
}
