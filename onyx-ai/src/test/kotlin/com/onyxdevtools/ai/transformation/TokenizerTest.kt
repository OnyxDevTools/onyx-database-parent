package com.onyxdevtools.ai.transformation

import kotlin.test.*

class BPETokenizerTest {

    private lateinit var vocabulary: Vocabulary
    private lateinit var tokenizer: BPETokenizer

    @BeforeTest
    fun setUp() {
        vocabulary = MockVocabulary()
        tokenizer = BPETokenizer(vocabulary)
    }

    @Test
    fun `test tokenize empty string`() {
        val tokens = tokenizer.tokenize("")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `test tokenize only whitespace`() {
        val tokens = tokenizer.tokenize("   \t\n")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `test tokenize special tokens`() {
        val text = "[CLS] [SEP] [UNK]"
        val tokens = tokenizer.tokenize(text)
        assertEquals(listOf("[CLS]", "[SEP]", "[UNK]"), tokens)
    }

    @Test
    fun `test tokenize code snippet kotlin if statement`() {
        val text = "if (x == 5);"
        val expected = listOf("if", "(", "x", "==", "5", ")", ";")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize code snippet typescript let assignment`() {
        val text = "let y = 10 + z;"
        val expected = listOf("let", "y", "=", "10", "+", "z", ";")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize code snippet java class declaration`() {
        val text = "public class Test {}"
        val expected = listOf("public", "class", "Test", "{", "}")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize code snippet swift function`() {
        val text = "func add(a: Int, b: Int) -> Int"
        val expected = listOf("func", "add", "(", "a", ":", "Int", ",", "b", ":", "Int", ")", "->", "Int")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize code snippet javascript const`() {
        val text = "const pi = 3.14;"
        val expected = listOf("const", "pi", "=", "3.14", ";")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math integral`() {
        val text = "\u222B x dx"
        val expected = listOf("<|math_\u222B|>", "x", "dx")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math summation`() {
        val text = "\u2211_{i=1}^n i"
        val expected = listOf("<|math_\u2211|>", "_", "{", "i", "=", "1", "}", "^", "n", "i")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math fractions`() {
        val text = "1/2 + 3/4"
        val expected = listOf("<|math_frac_1_2|>", "+", "<|math_frac_3_4|>")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math pi approx`() {
        val text = "\u03C0 \u2248 3.14"
        val expected = listOf("<|math_\u03C0|>", "<|math_\u2248|>", "3.14")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math greek letters`() {
        val text = "\u03B1 + \u03B2 = \u03B3"
        val expected = listOf("<|math_\u03B1|>", "+", "<|math_\u03B2|>", "=", "<|math_\u03B3|>")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize with normalization`() {
        val tokenizerWithNorm = BPETokenizer(vocabulary, normalize = true, removeDiacritics = true, caseSensitive = false)
        val text = "Café Example"
        val tokens = tokenizerWithNorm.tokenize(text)
        assertEquals(listOf("cafe", "example"), tokens)
    }

    @Test
    fun `test tokenize with custom keywords`() {
        val customKeywords = setOf("custom", "keyword")
        val tokenizerWithCustom = BPETokenizer(vocabulary, customKeywords = customKeywords)
        val text = "custom keyword test"
        val tokens = tokenizerWithCustom.tokenize(text)
        assertEquals(listOf("custom", "keyword", "test"), tokens)
    }

    @Test
    fun `test BPE subword tokenization helloworld`() {
        val word = "helloworld"
        val expected = listOf("hello", "##world")
        val tokens = tokenizer.bpe(word)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test BPE unknown word`() {
        val word = "unknownword"
        val expected = listOf("unknownword")
        val tokens = tokenizer.bpe(word)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test BPE long word`() {
        val word = "a".repeat(101)
        val expected = listOf("a") + List(100) { "##a" }
        val tokens = tokenizer.bpe(word)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test encode single text`() {
        val text = "hello world"
        val encoded = tokenizer.encode(text)
        val expectedTokens = listOf("[CLS]", "hello", "world", "[SEP]")
        val expectedIds = expectedTokens.map { vocabulary.getId(it) }
        assertEquals(expectedIds, encoded)
    }

    @Test
    fun `test encode paired text`() {
        val text1 = "hello"
        val text2 = "world"
        val encoded = tokenizer.encode(text1, text2)
        val expectedTokens = listOf("[CLS]", "hello", "[SEP]", "world", "[SEP]")
        val expectedIds = expectedTokens.map { vocabulary.getId(it) }
        assertEquals(expectedIds, encoded)
    }

    @Test
    fun `test decode`() {
        val ids = listOf(vocabulary.getId("[CLS]") , vocabulary.getId("hello") , vocabulary.getId("world") , vocabulary.getId("[SEP]"))
        val decoded = tokenizer.decode(ids)
        assertEquals("hello world", decoded)
    }

    @Test
    fun `test decode with subwords`() {
        val ids = listOf(vocabulary.getId("he") , vocabulary.getId("##llo"))
        val decoded = tokenizer.decode(ids)
        assertEquals("hello", decoded)
    }

    @Test
    fun `test error handling in tokenize`() {
        val longText = "a".repeat(101)
        val tokens = tokenizer.tokenize(longText)
        val expected = listOf("a") + List(100) { "##a" }
        assertEquals(expected, tokens)
    }

    @Test
    fun `test unknown tokens in encode`() {
        val text = "unknownToken"
        val encoded = tokenizer.encode(text)
        assertEquals(listOf(vocabulary.getId("[CLS]"), vocabulary.getId("unknownToken"), vocabulary.getId("[SEP]")), encoded)
    }

    @Test
    fun `test multi-character operators`() {
        val text = "x += 5 == y && z"
        val expected = listOf("x", "+=", "5", "==", "y", "&&", "z")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test greek letters and sets`() {
        val text = "\u2200 x \u2208 \u211D"
        val tokens = tokenizer.tokenize(text)
        assertEquals(listOf("<|math_\u2200|>", "x", "<|math_\u2208|>", "<|math_\u211D|>"), tokens)
    }
}

// Simple mock Vocabulary implementation for testing
class MockVocabulary : Vocabulary {
    private val tokenToId = mutableMapOf<String, Int>()
    private val idToToken = mutableMapOf<Int, String>()
    private var nextId = 0

    init {
        // Add special tokens
        addToken("[CLS]")
        addToken("[SEP]")
        addToken("[UNK]")
        addToken("[PAD]")
        addToken("[MASK]")
        addToken("<|startoftext|>")
        addToken("<|endoftext|>")
        addToken("<|sep|>")

        // Add some common tokens for BPE testing
        addToken("hello")
        addToken("he")
        addToken("llo")
        addToken("wor")
        addToken("ld")
        addToken("world")
        addToken("##llo")
        addToken("##wor")
        addToken("##ld")
        addToken("##world")

        // Add code-related tokens
        addToken("if")
        addToken("==")
        addToken("5")
        addToken("x")
        addToken("(")
        addToken(")")
        addToken(";")

        // Add math tokens
        addToken("<|math_\u222B|>")
        addToken("<|math_frac_1_2|>")
        addToken("<|math_\u2211|>" )

        // Add tokens needed for tests to pass
        addToken("x")
        addToken("y")
        addToken("z")
        addToken("Test")
        addToken("add")
        addToken("a")
        addToken("##a")
        addToken("b")
        addToken("Int")
        addToken("pi")
        addToken("dx")
        addToken("_")
        addToken("i")
        addToken("n")
        addToken("cafe")
        addToken("example")
        addToken("test")
        addToken("custom")
        addToken("keyword")
        addToken("let")
        addToken("10")
        addToken("+")
        addToken("public")
        addToken("class")
        addToken("func")
        addToken(":")
        addToken(",")
        addToken("->")
        addToken("const")
        addToken("3.14")
        addToken("1")
        addToken("^")
        addToken("<|math_\u03C0|>")
        addToken("<|math_\u2248|>")
        addToken("<|math_\u03B1|>")
        addToken("<|math_\u03B2|>")
        addToken("<|math_\u03B3|>")
        addToken("<|math_frac_3_4|>")
        addToken("<|math_\u2200|>")
        addToken("<|math_\u2208|>")
        addToken("<|math_\u211D|>")
    }

    override fun getId(token: String): Int = tokenToId[token] ?: addToken(token)

    override fun getToken(id: Int): String? = idToToken[id]

    override fun findId(substr: String): Int? = tokenToId[substr]

    private fun addToken(token: String): Int {
        if (token !in tokenToId) {
            val id = nextId++
            tokenToId[token] = id
            idToToken[id] = token
        }
        return tokenToId[token]!!
    }
}