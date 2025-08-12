package com.onyxdevtools.ai.transformation

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.io.ByteArrayOutputStream

/**
 * Byte-Pair Encoding (BPE) Tokenizer implementation (drop-in replacement).
 *
 * Improvements:
 * - Greedy longest-prefix matching with "##" continuation pieces
 * - Byte-level UTF-8 fallback tokens <0xHH> to avoid [UNK] for any Unicode
 * - Pretokenization on camelCase/snake_case/digit boundaries
 * - Tiny word-piece cache
 * - Preserves whitespace as tokens (" " or "\n") and removes decode auto-spacing
 */
class BPETokenizer(
    private val vocabulary: Vocabulary,
    private val unkToken: String = "[UNK]",
    private val maxInputCharsPerWord: Int = 100,
    private val caseSensitive: Boolean = true,
    private val normalize: Boolean = false,
    private val removeDiacritics: Boolean = false,
    customKeywords: Set<String> = emptySet()
) : Tokenizer {

    // Add near top of the class
    private val commonPieces = setOf(
        // ===== Whole words (top English stopwords + frequent content words) =====
        "the","and","to","of","a","in","that","is","it","for","on","was","with","as","I","he","be","at","by","are",
        "this","have","from","or","one","had","word","but","not","what","all","were","we","when","your","can","there","an",
        "which","their","said","if","do","will","each","about","how","up","out","them","then","she","many","some","so","these",
        "would","other","into","has","more","time","who","no","than","could","my","now","only","its","over","also","two","first",
        "after","even","most","may","like","new","such","because","any","work","people","make","made","back","good","me","our",
        "very","down","just","see","way","well","know","must","where","before","much","through","go","mr","mrs","man","little",
        "long","came","own","great","never","again","under","found","day","nothing","every","home","thought","those","small",
        "here","left","right","house","place","another","last","while","young","without","yet","ever","until","something","perhaps",
        "why","between","few","once","looked","being","love","face","soon","give","took","still","set","hand","eyes","night",
        "around","why","ask","answer","tell","told","called","name","room","door","king","queen","cat","rabbit","garden","river",
        "story","chapter","book","question","answer","beginning","end",
        // Contractions / helpers
        "it's","don't","didn't","can't","won't","isn't","aren't","wasn't","weren't","I've","I'm","you're","we're","they're",
        "I've","you've","we've","they've","I'd","you'd","he'd","she'd","we'd","they'd","I'll","you'll","he'll","she'll","we'll","they'll",

        // ===== Common prefixes (non-continued) =====
        "pre","re","un","in","im","il","ir","dis","non","mis","over","under","sub","inter","trans","super","semi","anti","auto","bio",
        "co","con","de","en","em","fore","intra","macro","micro","multi","post","pro","sub","tri","ultra","uni","hyper","hypo",

        // ===== Common suffixes (as continuations) =====
        "##s","##es","##ed","##ing","##er","##est","##ly","##ity","##ment","##tion","##sion","##able","##ible","##al","##ial","##ous",
        "##ious","##less","##ful","##ness","##ize","##ise","##ised","##ized","##izing","##ising","##ive","##ance","##ence","##ant","##ent",
        "##ary","##ory","##ship","##ward","##wards","##hood","##dom","##ish","##ment","##ical","##ically","##tional","##ational",
        "##ingly","##edly","##nesses","##abilities","##ization","##isations","##ist","##ists","##ism","##isms","##ative","##atively",

        // ===== Very frequent short pieces (roots + bridges) =====
        "th","he","an","re","on","er","en","ti","st","ar","al","or","at","nd","to","nt","ha","es","se","le","is","it","ed","of","ou","as",
        "me","li","ra","ve","ro","in","ea","te","ta","la","co","di","ri","io","om","no","ne","hi","ce","ch","ll","us","de","lo","pe","ac",
        "##th","##he","##an","##re","##on","##er","##en","##ti","##st","##ar","##al","##or","##at","##nd","##to","##nt","##ha","##es","##se",
        "##le","##is","##it","##ed","##of","##ou","##as","##me","##li","##ra","##ve","##ro","##in","##ea","##te","##ta","##la","##co","##di",
        "##ri","##io","##om","##no","##ne","##hi","##ce","##ch","##ll","##us","##de","##lo","##pe","##ac",

        // ===== Common function words as continuations =====
        "##the","##and","##to","##of","##in","##that","##is","##it","##for","##on","##was","##with","##as","##he","##be","##at","##by","##are",
        "##this","##have","##from","##or","##one","##but","##not","##all","##we","##you","##they","##she","##his","##her","##my","##our","##their",

        // ===== Frequent verb stems =====
        "look","come","go","make","take","know","think","see","want","give","find","tell","ask","work","call","need","feel","become","leave","put",
        "mean","keep","let","begin","seem","help","talk","turn","start","show","hear","play","run","move","live","believe","bring","happen","write",
        "provide","sit","stand","lose","pay","meet","include","continue","set","learn","change","lead","understand","watch","follow","stop",
        "create","speak","read","allow","add","spend","grow","open","walk","win","offer","remember","love","wait","consider","appear","buy","serve",
        "send","expect","build","stay","fall","cut","reach","kill",

        // Continuations for verbs
        "##look","##come","##make","##take","##know","##think","##see","##want","##give","##find","##tell","##ask","##work","##call","##need","##feel",
        "##become","##leave","##put","##mean","##keep","##begin","##seem","##help","##talk","##turn","##start","##show","##hear","##play","##run","##move",
        "##live","##believe","##bring","##happen","##write","##provide","##sit","##stand","##lose","##pay","##meet","##include","##continue","##set",
        "##learn","##change","##lead","##understand","##watch","##follow","##stop","##create","##speak","##read","##allow","##add","##spend","##grow",
        "##open","##walk","##win","##offer","##remember","##love","##wait","##consider","##appear","##buy","##serve","##send","##expect","##build",
        "##stay","##fall","##cut","##reach","##kill",

        // ===== Frequent nouns =====
        "time","year","people","way","day","man","thing","woman","life","child","world","school","state","family","student","group","country","problem","hand",
        "part","place","case","week","company","system","program","question","work","government","number","night","point","home","water","room","mother","area",
        "money","story","fact","month","lot","right","study","book","eye","job","word","business","issue","side","kind","head","house","service","friend","father",
        "power","hour","game","line","end","member","law","car","city","community","name","president","team","minute","idea","kid","body","information","back",
        "parent","face","others","level","office","door","health","person","art","war","history","party","result","change","morning","reason","research","girl",
        "guy","moment","air","teacher","force","education","foot",

        // Continuations for nouns/adjectives/adverbs
        "##time","##year","##people","##way","##day","##man","##thing","##woman","##life","##child","##world","##school","##state","##family","##group",
        "##country","##problem","##hand","##part","##place","##case","##week","##company","##system","##program","##question","##work","##number","##night",
        "##point","##home","##water","##room","##mother","##area","##money","##story","##fact","##month","##lot","##right","##study","##book","##eye","##job",
        "##word","##business","##issue","##side","##kind","##head","##house","##service","##friend","##father","##power","##hour","##game","##line","##end",
        "##member","##law","##car","##city","##community","##name","##team","##minute","##idea","##kid","##body","##information","##back","##parent","##face",
        "##level","##office","##door","##health","##person","##art","##war","##history","##party","##result","##change","##morning","##reason","##research",
        "##girl","##guy","##moment","##air","##teacher","##force","##education","##foot",

        // ===== Useful connectors and articles =====
        "of","to","in","for","on","with","at","from","into","about","than","after","before","between","through","during","under","against","without","within",
        "##of","##to","##in","##for","##on","##with","##at","##from","##into","##about","##than","##after","##before","##between","##through","##during",
        "##under","##against","##without","##within",

        // ===== Punctuation helpers (as full tokens, not byte tokens) =====
        ",",".",";","!",":","?","(",")","[","]","{","}","\"","'","-","–","—","…","/","&","%","$","@","#","*","+","=","<",">",
        "##,","##.","##;","##!","##:","##?","##)","##]","##}","##\"","##'","##-","##–","##—","##…","##/","##&","##%","##$","##@","##*","##+","##=","##>","##<",

        // ===== Numbers (simple) =====
        "0","1","2","3","4","5","6","7","8","9",
        "##0","##1","##2","##3","##4","##5","##6","##7","##8","##9",
    )

    private val defaultPunctuation: Set<String> by lazy {
        val ascii = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~"""
        buildSet {
            ascii.forEach { add(it.toString()) }
            // Quotes
            addAll(listOf("«","»","‹","›","‘","’","‚","‛","“","”","„","‟","\"","'"))
            // Dashes & ellipsis
            addAll(listOf("–","—","-","−","…"))
            // Parens/brackets/braces frequently used around specials
            addAll(listOf("[","]","{","}","(",")",",",".",";","!",":","?"))
            // Newlines we preserve as tokens
            addAll(listOf("\n", "\r\n"))
        }
    }

    // expose defaults you can seed into the vocab
    val defaultTokens: Set<String>
        get() {
            val ascii = (0x20..0x7E).map { it.toChar().toString() }.toSet()
            val asciiCont = ascii.map { "##$it" }.toSet()
            val bytes = (0..255).map { "<0x%02X>".format(it) }.toSet()
            val bytesCont = bytes.map { "##$it" }.toSet()

            return specialTokens +
                    operators.toSet() +
                    defaultPunctuation +
                    commonPieces +                 // << add this
                    ascii + asciiCont +
                    bytes + bytesCont
        }

    private val specialTokens = setOf(
        "[PAD]", "[CLS]", "[SEP]", "[UNK]", "[MASK]",
        "[SOT]", "[EOT]", "[U]", "[A]"
    )

    private val specialTokensPattern = specialTokens
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

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
        // 1: special tokens (must be first)
        "(" + specialTokensPattern + ")|" +
                // 2: whitespace
                "(\\s+)|" +
                // 3: keywords
                "(\\b(?:${keywords.joinToString("|")})\\b)|" +
                // 4: operators
                "(${operators.joinToString("|") { Regex.escape(it) }})|" +
                // 5: identifiers
                "(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)|" +
                // 6: numbers
                "(\\b\\d+\\b)|" +
                // 7: punctuation (ASCII + smart quotes + dashes + ellipsis)
                "([\\[\\](){}.,;:!?\"'“”‘’«»‹›–—…])|" +
                // 8: math symbols
                "(${mathSymbols.joinToString("|") { Regex.escape(it) }})|" +
                // 9: fractions
                "(\\d+/\\d+)"
    )


    // --- New internals ---
    private val pieceCache = ConcurrentHashMap<String, List<String>>()
    private val byteTokens: Set<String> = (0..255).map { "<0x%02X>".format(it) }.toSet()
    private val byteTokRegex = Regex("""^(?:##)?<0x([0-9A-Fa-f]{2})>$""")

    private fun normalizeIfNeeded(text: String): String {
        if (!normalize && caseSensitive && !removeDiacritics) return text
        var t = text
        if (normalize) t = Normalizer.normalize(t, Normalizer.Form.NFD)
        if (removeDiacritics) t = t.replace(Regex("\\p{M}"), "")
        if (!caseSensitive) t = t.lowercase()
        return t
    }

    private fun pretokenizeWord(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val parts = word.split('_')
        val out = mutableListOf<String>()
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty()) {
                out += "_"
                continue
            }
            val regex = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=\\d)|(?<=\\d)(?=[A-Za-z])")
            val sub = part.split(regex)
            out += sub
            if (i < parts.lastIndex) out += "_"
        }
        return out
    }

    private fun greedyMatch(s: String, isContinuation: Boolean): Pair<String, Int>? {
        var len = s.length
        while (len > 0) {
            val piece = s.substring(0, len)
            val contTok = "##$piece"
            if (isContinuation && vocabulary.findId(contTok) != null) return contTok to len
            if (vocabulary.findId(piece) != null) return piece to len
            len--
        }
        return null
    }

    private fun bytesFallbackChar(c: Char, isContinuation: Boolean, out: MutableList<String>) {
        // Prefer plain ASCII char tokens if available (so the model learns normal text)
        if (c.code in 0x20..0x7E) {
            val s = c.toString()
            val cont = "##$s"
            val id = vocabulary.findId(if (isContinuation) cont else s) ?: vocabulary.findId(s)
            if (id != null) {
                out += if (isContinuation && vocabulary.findId(cont) != null) cont else s
                return
            }
            // If ASCII but not in vocab, fall through to byte tokens.
        }

        // Non-ASCII or not representable: emit bytes
        val bytes = c.toString().encodeToByteArray()
        for ((idx, b) in bytes.withIndex()) {
            val tok = "<0x%02X>".format(b.toInt() and 0xFF)
            val finalTok = if (isContinuation || out.isNotEmpty() || idx > 0) "##$tok" else tok
            out += finalTok
        }
    }

    // --- Public API ---

    override fun tokenize(text: String): List<String> {
        return try {
            val normalizedText = normalizeIfNeeded(text)
            val tokens = mutableListOf<String>()
            var lastEnd = 0

            for (match in tokenPattern.findAll(normalizedText)) {
                val start = match.range.first
                if (start > lastEnd) {
                    val unknownText = normalizedText.substring(lastEnd until start)
                    tokens.addAll(bpe(unknownText))
                }

                val g = match.groupValues
                when {
                    g[1].isNotEmpty() -> tokens.add(g[1])                 // special token (atomic)
                    g[2].isNotEmpty() -> tokens.add(if (g[2].any { it == '\n' }) "\n" else " ")
                    g[3].isNotEmpty() -> tokens.add(g[3])                 // keyword
                    g[4].isNotEmpty() -> tokens.add(g[4])                 // operator
                    g[5].isNotEmpty() -> tokens.addAll(bpe(g[5]))         // identifier -> BPE
                    g[6].isNotEmpty() -> tokens.add(g[6])                 // number
                    g[7].isNotEmpty() -> tokens.add(g[7])                 // punctuation (incl. smart)
                    g[8].isNotEmpty() -> tokens.add("<|math_${g[8]}|>")
                    g[9].isNotEmpty() -> {
                        val m = Regex("(\\d+)/(\\d+)").matchEntire(g[9])
                        if (m != null) {
                            val (num, den) = m.destructured
                            tokens.add("<|math_frac_${num}_${den}|>")
                        } else tokens.add(g[9])
                    }
                }
                lastEnd = match.range.last + 1
            }

            if (lastEnd < normalizedText.length) {
                tokens.addAll(bpe(normalizedText.substring(lastEnd)))
            }

            tokens
        } catch (e: Exception) {
            println("Error during tokenization: ${e.message}")
            listOf(unkToken)
        }
    }

    fun bpe(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        if (word.length > maxInputCharsPerWord) return listOf(unkToken)
        if (specialTokens.contains(word)) return listOf(word)

        pieceCache[word]?.let { return it }
        vocabulary.findId(word)?.let { // whole word known
            pieceCache[word] = listOf(word)
            return listOf(word)
        }

        val pieces = mutableListOf<String>()
        val chunks = pretokenizeWord(word)

        for ((i, chunk) in chunks.withIndex()) {
            if (chunk == "_") {
                val underscoreTok = if (i == 0 && pieces.isEmpty()) "_" else "##_"
                when {
                    vocabulary.findId(underscoreTok) != null -> {
                        pieces += underscoreTok
                        continue
                    }
                    vocabulary.findId("_") != null -> {
                        val tok = if (pieces.isEmpty()) "_" else "##_" // prefer continuation if not first
                        pieces += tok
                        continue
                    }
                }
                // else fall through and treat "_" as raw text
            }

            var s = chunk
            var continuation = (i > 0 || pieces.isNotEmpty())

            while (s.isNotEmpty()) {
                val match = greedyMatch(s, continuation)
                if (match != null) {
                    val (tok, len) = match
                    pieces += tok
                    s = s.substring(len)
                    continuation = true
                } else {
                    // fallback 1: single char token (with continuation preferred)
                    val c = s.substring(0, 1)
                    val cont = "##$c"
                    val id = vocabulary.findId(if (continuation) cont else c)
                        ?: vocabulary.findId(c)
                    if (id != null) {
                        pieces += if (continuation && vocabulary.findId(cont) != null) cont else c
                        s = s.substring(1)
                        continuation = true
                    } else {
                        // fallback 2: byte tokens for this char
                        bytesFallbackChar(c[0], continuation, pieces)
                        s = s.substring(1)
                        continuation = true
                    }
                }
            }
        }

        if (pieces.isEmpty()) return listOf(unkToken)
        pieceCache[word] = pieces
        return pieces
    }

    override fun encode(text: String): List<Int> {
        val tokens = tokenize(text)
        val ids = mutableListOf<Int>()
        ids += vocabulary.getId("[CLS]")
        ids += tokens.map { vocabulary.getId(it) }
        ids += vocabulary.getId("[SEP]")
        return ids
    }

    override fun encode(text: String, textPair: String): List<Int> {
        val t1 = tokenize(text)
        val t2 = tokenize(textPair)
        val all = listOf("[CLS]") + t1 + listOf("[SEP]") + t2 + listOf("[SEP]")
        return all.map { vocabulary.getId(it) }
    }

    override fun decode(ids: List<Int>): String {
        val toks = ids.mapNotNull { vocabulary.getToken(it) }
        val sb = StringBuilder()
        val byteBuf = ByteArrayOutputStream()

        fun flushBytes() {
            if (byteBuf.size() > 0) {
                sb.append(byteBuf.toByteArray().toString(Charsets.UTF_8))
                byteBuf.reset()
            }
        }

        for (tok in toks) {
            if (tok in specialTokens) continue
            if (tok == " " || tok == "\n") { flushBytes(); sb.append(tok); continue }
            val bt = byteTokRegex.matchEntire(tok)
            if (bt != null) { byteBuf.write(bt.groupValues[1].toInt(16)); continue } else flushBytes()
            if (tok.startsWith("##")) sb.append(tok.substring(2)) else sb.append(tok)
        }
        flushBytes()
        var out = sb.toString()

        // Collapse multiple spaces
        out = out.replace(Regex(" {2,}"), " ")

        // No space before closing punctuation (ASCII + unicode)
        out = out.replace(Regex(" *([,.;:!?])"), "$1")
        out = out.replace(Regex(" *(”|’|»|›)"), "$1")

        // No space after opening quotes/brackets
        out = out.replace(Regex("(“|‘|«|‹) *"), "$1")

        // Tighten around parens/brackets/braces
        out = out.replace(Regex("\\( "), "(")
            .replace(Regex(" \\)"), ")")
            .replace(Regex("\\[ "), "[")
            .replace(Regex(" \\]"), "]")
            .replace(Regex("\\{ "), "{")
            .replace(Regex(" \\}"), "}{")

        // Ellipsis & dashes
        out = out.replace(Regex(" *…"), "…")
            .replace(Regex(" *(–|—) *"), " $1 ")

        // Drop literal bracketed specials if the model spelled them out
        out = out.replace(Regex("""\[(?:PAD|CLS|SEP|UNK|MASK|SOT|EOT)\]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return out
    }

    fun encodeCausal(text: String, addSot: Boolean = true): List<Int> {
        val ids = tokenize(text).map { vocabulary.getId(it) }
        return if (addSot) {
            val sot = vocabulary.getId("[SOT]")
            listOf(sot) + ids
        } else {
            ids
        }
    }

    fun extractTokenFrequencies(text: String): Map<String, Int> {
        val tokenCounts = mutableMapOf<String, Int>()
        try {
            val normalizedText = normalizeIfNeeded(text)
            var lastEnd = 0

            for (match in tokenPattern.findAll(normalizedText)) {
                val start = match.range.first
                if (start > lastEnd) {
                    val unknownText = normalizedText.substring(lastEnd until start)
                    unknownText.forEach { ch ->
                        val str = ch.toString()
                        tokenCounts[str] = tokenCounts.getOrDefault(str, 0) + 1
                    }
                }

                val g = match.groupValues
                val token = when {
                    g[1].isNotEmpty() -> if (g[1].any { it == '\n' }) "\n" else " " // count whitespace
                    g[2].isNotEmpty() -> g[2]
                    g[3].isNotEmpty() -> g[3]
                    g[4].isNotEmpty() -> g[4]
                    g[5].isNotEmpty() -> g[5]
                    g[6].isNotEmpty() -> g[6]
                    g[7].isNotEmpty() -> "<|math_${g[7]}|>"
                    g[8].isNotEmpty() -> {
                        val m = Regex("(\\d+)/(\\d+)").matchEntire(g[8])
                        if (m != null) {
                            val (num, den) = m.destructured
                            "<|math_frac_${num}_${den}|>"
                        } else g[8]
                    }
                    else -> null
                }
                if (token != null) {
                    tokenCounts[token] = tokenCounts.getOrDefault(token, 0) + 1
                }
                lastEnd = match.range.last + 1
            }

            if (lastEnd < normalizedText.length) {
                normalizedText.substring(lastEnd).forEach { ch ->
                    val str = ch.toString()
                    tokenCounts[str] = tokenCounts.getOrDefault(str, 0) + 1
                }
            }
        } catch (e: Exception) {
            println("Error during token frequency extraction: ${e.message}")
        }
        return tokenCounts
    }

    fun extractUniqueTokens(text: String): Set<String> = extractTokenFrequencies(text).keys
}
