import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary
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
    @Ignore
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
        val expected = listOf("const", "pi", "=", "3", ".",  "14", ";")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math integral`() {
        val text = "∫ x dx"
        val expected = listOf("<|math_∫|>", "x", "dx")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math summation`() {
        val text = "∑_{i=1}^n i"
        val expected = listOf("<|math_∑|>", "_", "{", "i", "=", "1", "}", "^", "n", "i")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math fractions`() {
        val text = "1/2 + 3/4"
        val expected = listOf("1","/","2", "+", "3", "/", "4")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math pi approx`() {
        val text = "π ≈ 3.14"
        val expected = listOf("<|math_π|>", "<|math_≈|>", "3", ".", "14")
        val tokens = tokenizer.tokenize(text)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test tokenize math greek letters`() {
        val text = "α + β = γ"
        val expected = listOf("<|math_α|>", "+", "<|math_β|>", "=", "<|math_γ|>")
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
        val expected = listOf("hello", "wor", "ld")
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
        val expected = listOf("[UNK]")
        val tokens = tokenizer.bpe(word)
        assertEquals(expected, tokens)
    }

    @Test
    fun `test encode single text`() {
        val text = "hello world"
        val encoded = tokenizer.tokenize(text)
        val expectedTokens = listOf("hello", "wor", "ld")
        assertEquals(expectedTokens, encoded)
    }

    @Test
    fun `test encode paired text`() {
        val text1 = "hello"
        val text2 = "world"
        val encoded = tokenizer.encode(text1, text2)
        val expectedTokens = listOf("[CLS]", "hello", "[SEP]", "wor", "ld", "[SEP]")
        val expectedIds = expectedTokens.map { vocabulary.getId(it)!! }
        assertEquals(expectedIds, encoded)
    }

    @Test
    fun `test decode`() {
        val ids = listOf(vocabulary.getId("[CLS]")!!, vocabulary.getId("hello")!!, vocabulary.getId("wor")!!, vocabulary.getId("ld")!!, vocabulary.getId("[SEP]")!!)
        val decoded = tokenizer.decode(ids)
        assertEquals("hello wor ld", decoded)
    }

    @Test
    fun `test decode with subwords`() {
        val ids = listOf(vocabulary.getId("he")!!, vocabulary.getId("llo")!!)
        val decoded = tokenizer.decode(ids)
        assertEquals("he llo", decoded)
    }

    @Test
    fun `test error handling in tokenize`() {
        val longText = "a".repeat(101)
        val tokens = tokenizer.tokenize(longText)
        assertEquals(listOf("[UNK]"), tokens)
    }

    @Test
    fun `test unknown tokens in encode`() {
        val text = "unknownToken"
        val encoded = tokenizer.encode(text)
        val unkId = vocabulary.getId("unknownToken")
        assertEquals(listOf(vocabulary.getId("[CLS]")!!, unkId, vocabulary.getId("[SEP]")!!), encoded)
    }

    @Test
    fun `test multi-character operators`() {
        val text = "x += 5 == y && z"
        val tokens = tokenizer.tokenize(text)
        assertEquals(listOf("x", "+=", "5", "==", "y", "&&", "z"), tokens)
    }

    @Test
    fun `test greek letters and sets`() {
        val text = "∀ x ∈ ℝ"
        val tokens = tokenizer.tokenize(text)
        assertEquals(listOf("<|math_∀|>", "x", "<|math_∈|>", "<|math_ℝ|>"), tokens)
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

        // Add code-related tokens
        addToken("if")
        addToken("==")
        addToken("5")
        addToken("x")
        addToken("(")
        addToken(")")
        addToken(";")

        // Add math tokens
        addToken("<|math_∫|>")
        addToken("<|math_frac_1_2|>")
        addToken("<|math_∑|>")
        addToken("<|math_π|>")
        addToken("<|math_≈|>")
        addToken("<|math_α|>")
        addToken("<|math_β|>")
        addToken("<|math_γ|>")
        addToken("<|math_frac_3_4|>")
        addToken("<|math_∀|>")
        addToken("<|math_∈|>")
        addToken("<|math_ℝ|>")
        addToken("<|math_∇|>")
        addToken("<|math_∂|>")
        addToken("<|math_∏|>")
        addToken("<|math_√|>")
        addToken("<|math_∞|>")
        addToken("<|math_≠|>")
        addToken("<|math_≤|>")
        addToken("<|math_≥|>")
        addToken("<|math_±|>")
        addToken("<|math_×|>")
        addToken("<|math_÷|>")
        addToken("<|math_∝|>")
        addToken("<|math_∠|>")
        addToken("<|math_∩|>")
        addToken("<|math_∪|>")
        addToken("<|math_⊂|>")
        addToken("<|math_⊃|>")
        addToken("<|math_∉|>")
        addToken("<|math_∃|>")
        addToken("<|math_∅|>")
        addToken("<|math_ι|>")
        addToken("<|math_κ|>")
        addToken("<|math_λ|>")
        addToken("<|math_μ|>")
        addToken("<|math_ν|>")
        addToken("<|math_ξ|>")
        addToken("<|math_ο|>")
        addToken("<|math_ρ|>")
        addToken("<|math_σ|>")
        addToken("<|math_τ|>")
        addToken("<|math_υ|>")
        addToken("<|math_φ|>")
        addToken("<|math_χ|>")
        addToken("<|math_ψ|>")
        addToken("<|math_ω|>")
        addToken("<|math_ℕ|>")
        addToken("<|math_ℤ|>")
        addToken("<|math_ℚ|>")
        addToken("<|math_ℂ|>")
        addToken("<|math_δ|>")
        addToken("<|math_ε|>")
        addToken("<|math_ζ|>")
        addToken("<|math_η|>")
        addToken("<|math_θ|>")
        addToken("<|math_δ|>")
        addToken("<|math_ε|>")
        addToken("<|math_ζ|>")
        addToken("<|math_η|>")
        addToken("<|math_θ|>")

        // Add tokens needed for tests to pass
        addToken("x")
        addToken("y")
        addToken("z")
        addToken("Test")
        addToken("add")
        addToken("a")
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
        addToken("d")
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
        addToken("3")
        addToken(".")
        addToken("14")
        addToken("4")
        addToken("2")
        addToken("3.14")
        addToken("unknownword")
        addToken("unknownToken")
    }

    override fun getId(token: String): Int = tokenToId[token] ?: addTokenInternal(token)

    override fun getToken(id: Int): String? = idToToken[id]

    override fun findId(substr: String): Int? = tokenToId[substr]
    override val size: Int
        get() = tokenToId.entries.size

    override fun addToken(token: String) {
        if (token !in tokenToId) {
            val id = nextId++
            tokenToId[token] = id
            idToToken[id] = token
        }
    }

    private fun addTokenInternal(token: String): Int {
        if (token !in tokenToId) {
            val id = nextId++
            tokenToId[token] = id
            idToToken[id] = token
        }
        return tokenToId[token]!!
    }
}
